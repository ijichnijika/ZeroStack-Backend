package com.ZeroStack.core.cache;

import cn.hutool.json.JSONUtil;
import com.ZeroStack.constant.PresetPromptConstant;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PresetPromptCacheServiceTest {

    private static final long SOURCE_APP_ID = 101L;
    private static final long TARGET_APP_ID = 202L;

    @TempDir
    Path codeOutputRoot;

    private ConcurrentMapCacheManager cacheManager;
    private PresetPromptCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(PresetPromptConstant.CACHE_NAME);
        cacheService = new PresetPromptCacheService(cacheManager, codeOutputRoot);
    }

    @Test
    void shouldNormalizePromptAndIsolateGenerationType() {
        String prompt = presetPromptOf(CodeGenTypeEnum.MULTI_FILE);

        cacheService.putTextCode("  " + prompt + "  ", CodeGenTypeEnum.MULTI_FILE, "generated-code");

        assertThat(cacheService.getTextCode(prompt, CodeGenTypeEnum.MULTI_FILE))
                .contains("generated-code");
        assertThat(cacheService.getTextCode(prompt, CodeGenTypeEnum.HTML)).isEmpty();
    }

    @Test
    void shouldRestoreVueSourceFilesForAnotherApp() throws Exception {
        String prompt = presetPromptOf(CodeGenTypeEnum.VUE_PROJECT);
        Path sourceRoot = codeOutputRoot.resolve("vue_project_" + SOURCE_APP_ID);
        Files.createDirectories(sourceRoot.resolve("src"));
        Files.createDirectories(sourceRoot.resolve("node_modules"));
        Files.createDirectories(sourceRoot.resolve("dist"));
        Files.writeString(sourceRoot.resolve("package.json"), "{\"scripts\":{\"build\":\"vite build\"}}");
        Files.writeString(sourceRoot.resolve("src/App.vue"), "<template>dashboard</template>");
        Files.writeString(sourceRoot.resolve("node_modules/ignored.js"), "ignored");
        Files.writeString(sourceRoot.resolve("dist/index.html"), "ignored");
        List<String> streamMessages = List.of("{\"type\":\"AI_RESPONSE\",\"data\":\"done\"}");

        cacheService.putVueProject(prompt, streamMessages, SOURCE_APP_ID);
        List<String> restoredMessages = cacheService.restoreVueProject(prompt, TARGET_APP_ID).orElseThrow();

        Path targetRoot = codeOutputRoot.resolve("vue_project_" + TARGET_APP_ID);
        assertThat(restoredMessages).containsExactlyElementsOf(streamMessages);
        assertThat(targetRoot.resolve("package.json")).hasContent("{\"scripts\":{\"build\":\"vite build\"}}");
        assertThat(targetRoot.resolve("src/App.vue")).hasContent("<template>dashboard</template>");
        assertThat(targetRoot.resolve("node_modules")).doesNotExist();
        assertThat(targetRoot.resolve("dist")).doesNotExist();
    }

    @Test
    void shouldRejectUnsafeCacheBeforeCleaningExistingProject() throws Exception {
        String prompt = presetPromptOf(CodeGenTypeEnum.VUE_PROJECT);
        Path targetRoot = codeOutputRoot.resolve("vue_project_" + TARGET_APP_ID);
        Files.createDirectories(targetRoot);
        Path sentinel = targetRoot.resolve("existing.txt");
        Files.writeString(sentinel, "keep-me");
        String unsafePayload = JSONUtil.toJsonStr(Map.of(
                "streamMessages", List.of("message"),
                "projectFiles", Map.of("package.json", "{}", "../outside.txt", "unsafe")));
        Cache cache = cacheManager.getCache(PresetPromptConstant.CACHE_NAME);
        assertThat(cache).isNotNull();
        cache.put(prompt, unsafePayload);

        assertThat(cacheService.restoreVueProject(prompt, TARGET_APP_ID)).isEmpty();
        assertThat(sentinel).hasContent("keep-me");
        assertThat(codeOutputRoot.resolve("outside.txt")).doesNotExist();
        assertThat(cache.get(prompt)).isNull();
    }

    private String presetPromptOf(CodeGenTypeEnum codeGenType) {
        return PresetPromptConstant.PRESET_PROMPTS_MAP.entrySet().stream()
                .filter(entry -> entry.getValue() == codeGenType)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }
}
