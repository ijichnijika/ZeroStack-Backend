package com.ZeroStack.core.cache;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.ZeroStack.constant.AppConstant;
import com.ZeroStack.constant.PresetPromptConstant;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 预设提示词缓存服务。
 *
 * <p>文本模式缓存代码正文；Vue 模式额外缓存源文件快照，因为工具调用消息本身
 * 不会在缓存回放时再次执行文件写入。</p>
 */
@Slf4j
@Service
public class PresetPromptCacheService {

    private static final int MAX_VUE_FILE_COUNT = 200;
    private static final long MAX_VUE_SOURCE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_VUE_PAYLOAD_CHARS = 4 * 1024 * 1024;
    private static final int MAX_PATH_DEPTH = 20;
    private static final List<String> EXCLUDED_DIRECTORIES = List.of(
            "node_modules", "dist", ".git", ".cache");

    private final CacheManager cacheManager;
    private final Path codeOutputRoot;

    /**
     * 创建使用系统代码输出目录的缓存服务。
     *
     * @param cacheManager Spring 缓存管理器
     */
    @Autowired
    public PresetPromptCacheService(CacheManager cacheManager) {
        this(cacheManager, Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR));
    }

    PresetPromptCacheService(CacheManager cacheManager, Path codeOutputRoot) {
        this.cacheManager = cacheManager;
        this.codeOutputRoot = codeOutputRoot.toAbsolutePath().normalize();
    }

    /**
     * 获取文本代码缓存。
     *
     * @param prompt 用户提示词
     * @param codeGenType 代码生成类型
     * @return 命中的完整代码
     */
    public Optional<String> getTextCode(String prompt, CodeGenTypeEnum codeGenType) {
        String cacheKey = resolveCacheKey(prompt, codeGenType);
        if (cacheKey == null) {
            return Optional.empty();
        }
        String cachedCode = readCache(cacheKey);
        if (cachedCode != null && !cachedCode.isBlank()) {
            log.info("命中预设提示词文本缓存");
        }
        return Optional.ofNullable(cachedCode).filter(code -> !code.isBlank());
    }

    /**
     * 写入文本代码缓存，仅接受与预设路由一致的生成类型。
     *
     * @param prompt 用户提示词
     * @param codeGenType 代码生成类型
     * @param completeCode 完整代码
     */
    public void putTextCode(String prompt, CodeGenTypeEnum codeGenType, String completeCode) {
        String cacheKey = resolveCacheKey(prompt, codeGenType);
        if (cacheKey != null && completeCode != null && !completeCode.isBlank()
                && writeCache(cacheKey, completeCode)) {
            log.info("预设提示词代码生成完毕，已写入文本缓存");
        }
    }

    /**
     * 恢复 Vue 项目缓存到当前应用目录。
     *
     * @param prompt 用户提示词
     * @param appId 当前应用 ID
     * @return 用于前端和历史记录处理的原始 JSON 消息流
     */
    public Optional<List<String>> restoreVueProject(String prompt, long appId) {
        String cacheKey = resolveCacheKey(prompt, CodeGenTypeEnum.VUE_PROJECT);
        String cachedPayload = cacheKey == null ? null : readCache(cacheKey);
        if (cachedPayload == null || cachedPayload.isBlank()) {
            return Optional.empty();
        }
        VueCacheEntry cacheEntry = parseVueCacheEntry(cachedPayload);
        if (cacheEntry == null || !restoreProjectFiles(cacheEntry.projectFiles(), appId)) {
            evictCache(cacheKey);
            log.warn("Vue 预设缓存无效，已清理并回退到大模型生成");
            return Optional.empty();
        }
        log.info("命中 Vue 预设缓存，已恢复到 appId={}", appId);
        return Optional.of(cacheEntry.streamMessages());
    }

    /**
     * 缓存 Vue 的消息流和源文件快照。
     *
     * @param prompt 用户提示词
     * @param streamMessages 原始 JSON 消息流
     * @param appId 生成源文件所在应用 ID
     */
    public void putVueProject(String prompt, List<String> streamMessages, long appId) {
        String cacheKey = resolveCacheKey(prompt, CodeGenTypeEnum.VUE_PROJECT);
        if (cacheKey == null || streamMessages == null || streamMessages.isEmpty()) {
            return;
        }
        Map<String, String> projectFiles = collectProjectFiles(appId);
        if (!isCompleteVueSnapshot(projectFiles)) {
            return;
        }
        String payload = JSONUtil.toJsonStr(Map.of(
                "streamMessages", List.copyOf(streamMessages),
                "projectFiles", projectFiles));
        if (payload.length() <= MAX_VUE_PAYLOAD_CHARS && writeCache(cacheKey, payload)) {
            log.info("Vue 预设提示词生成完毕，已缓存消息流和项目文件");
        } else if (payload.length() > MAX_VUE_PAYLOAD_CHARS) {
            log.warn("Vue 项目缓存超过大小限制，跳过写入: {}", payload.length());
        }
    }

    private String resolveCacheKey(String prompt, CodeGenTypeEnum codeGenType) {
        String normalizedPrompt = PresetPromptConstant.normalizePrompt(prompt);
        return normalizedPrompt != null
                && PresetPromptConstant.getPresetPromptCodeGenType(normalizedPrompt) == codeGenType
                ? normalizedPrompt : null;
    }

    private Cache getCache() {
        return cacheManager.getCache(PresetPromptConstant.CACHE_NAME);
    }

    private String readCache(String cacheKey) {
        try {
            Cache cache = getCache();
            return cache == null ? null : cache.get(cacheKey, String.class);
        } catch (RuntimeException e) {
            log.warn("读取预设提示词缓存失败，将回退到正常生成: {}", e.getMessage());
            return null;
        }
    }

    private boolean writeCache(String cacheKey, String value) {
        try {
            Cache cache = getCache();
            if (cache == null) {
                return false;
            }
            cache.put(cacheKey, value);
            return true;
        } catch (RuntimeException e) {
            log.warn("写入预设提示词缓存失败，不影响本次生成: {}", e.getMessage());
            return false;
        }
    }

    private void evictCache(String cacheKey) {
        try {
            Cache cache = getCache();
            if (cache != null) {
                cache.evict(cacheKey);
            }
        } catch (RuntimeException e) {
            log.warn("清理无效预设提示词缓存失败: {}", e.getMessage());
        }
    }

    private VueCacheEntry parseVueCacheEntry(String cachedPayload) {
        try {
            Map<?, ?> payload = JSONUtil.toBean(cachedPayload, Map.class);
            Object streamMessages = payload.get("streamMessages");
            Object projectFiles = payload.get("projectFiles");
            if (!(streamMessages instanceof List<?> messages)
                    || !(projectFiles instanceof Map<?, ?> files)) {
                return null;
            }
            List<String> normalizedMessages = messages.stream()
                    .filter(String.class::isInstance).map(String.class::cast).toList();
            Map<String, String> normalizedFiles = normalizeProjectFiles(files);
            return normalizedMessages.isEmpty() || normalizedFiles.isEmpty()
                    ? null : new VueCacheEntry(normalizedMessages, normalizedFiles);
        } catch (Exception e) {
            log.warn("解析 Vue 预设缓存失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> normalizeProjectFiles(Map<?, ?> files) {
        Map<String, String> normalizedFiles = new LinkedHashMap<>();
        files.forEach((path, content) -> {
            if (path instanceof String && content instanceof String) {
                normalizedFiles.put((String) path, (String) content);
            }
        });
        return normalizedFiles;
    }

    private Map<String, String> collectProjectFiles(long appId) {
        Path projectRoot = vueProjectRoot(appId);
        if (!Files.isDirectory(projectRoot)) {
            return Map.of();
        }
        Map<String, String> projectFiles = new LinkedHashMap<>();
        long totalBytes = 0;
        int fileCount = 0;
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            Iterator<Path> iterator = paths.filter(Files::isRegularFile)
                    .filter(path -> isCacheableFile(projectRoot, path)).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                totalBytes += Files.size(file);
                if (++fileCount > MAX_VUE_FILE_COUNT || totalBytes > MAX_VUE_SOURCE_BYTES) {
                    log.warn("Vue 项目源文件超过缓存限制，跳过写入");
                    return Map.of();
                }
                projectFiles.put(projectRoot.relativize(file).toString(),
                        Files.readString(file, StandardCharsets.UTF_8));
            }
            return projectFiles;
        } catch (IOException | RuntimeException e) {
            log.warn("读取 Vue 项目文件用于缓存失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean isCacheableFile(Path projectRoot, Path file) {
        return isCacheableRelativePath(projectRoot.relativize(file));
    }

    private boolean isCacheableRelativePath(Path relativePath) {
        if (relativePath.toString().isBlank() || relativePath.startsWith("..")
                || relativePath.getNameCount() > MAX_PATH_DEPTH) {
            return false;
        }
        for (Path pathPart : relativePath) {
            if (EXCLUDED_DIRECTORIES.contains(pathPart.toString())) {
                return false;
            }
        }
        return true;
    }

    private boolean restoreProjectFiles(Map<String, String> projectFiles, long appId) {
        Path projectRoot = vueProjectRoot(appId);
        try {
            if (!isCompleteVueSnapshot(projectFiles)
                    || projectFiles.keySet().stream().anyMatch(path -> !isSafeRelativePath(path))) {
                return false;
            }
            FileUtil.del(projectRoot.toFile());
            Files.createDirectories(projectRoot);
            for (Map.Entry<String, String> file : projectFiles.entrySet()) {
                Path target = projectRoot.resolve(file.getKey()).normalize();
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("恢复 Vue 项目缓存失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean isCompleteVueSnapshot(Map<String, String> projectFiles) {
        return projectFiles != null && !projectFiles.isEmpty()
                && projectFiles.containsKey("package.json");
    }

    private boolean isSafeRelativePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        Path rawPath = Paths.get(filePath);
        Path normalizedPath = rawPath.normalize();
        return !rawPath.isAbsolute() && isCacheableRelativePath(normalizedPath);
    }

    private Path vueProjectRoot(long appId) {
        return codeOutputRoot.resolve("vue_project_" + appId).normalize();
    }

    private record VueCacheEntry(List<String> streamMessages, Map<String, String> projectFiles) {
    }
}
