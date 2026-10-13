package com.ZeroStack.core.prompt;

import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 小规模知识库的本地检索，不引入向量服务，不加载待审资料到模型上下文。
 */
@Component
@Slf4j
public class PromptKnowledgeBase {

    private static final List<String> CONTEXT_FIELDS = List.of("id", "type", "title", "content", "tags",
            "appliesTo", "boundary", "promptExample", "sourceUrls", "version");
    private static final Set<String> FIXED_IDS = Set.of("SCOPE-01", "SCOPE-02");
    private static final Set<String> DESIGN_TYPES = Set.of("scene", "palette", "style", "typography", "layout", "reasoning");
    private static final Set<String> TOPIC_TYPES = Set.of("scene", "reasoning", "palette", "style");
    // 只补充明确的网页设计同义表达，不靠业务词猜测页面用途。
    private static final Map<String, String> QUERY_ALIASES = Map.of(
            "待办清单", "效率工具", "产品落地页", "产品介绍与简单营销页",
            "信息密度高", "高密度看板", "咖啡店", "咖啡馆");
    private static final Set<String> MODIFICATION_TYPES = Set.of("rule", "upstream_rule", "typography", "motion",
            "official_pattern_case", "authored_prompt_case");
    private static final Set<String> MODIFICATION_TARGETS = Set.of("按钮", "表单", "输入框", "导航", "表格",
            "标题", "图片", "图标", "卡片", "弹窗", "标签页", "字体", "菜单", "进度条", "分页", "搜索框", "列表");
    // 类别名称和通用表达不能作为场景依据。
    private static final Set<String> STOP_WORDS = Set.of("一个", "帮我", "做个", "网站", "页面", "好看",
            "一点", "需要", "创建", "设计", "优化", "用户", "功能", "在线", "演示", "相关", "参考", "可选", "展示",
            "场景", "场景取舍", "场景推荐", "可选设计参考", "配色", "颜色角色", "字体", "布局", "风格", "动效");

    @Value("${prompt.optimization.knowledge-directory:../docx/references/RAG知识库}")
    private String knowledgeDirectory;

    @Resource
    private ObjectMapper objectMapper;

    private Snapshot snapshot;

    public String getPrinciples() {
        return getSnapshot().principles();
    }

    public ArrayNode retrieve(String prompt, String mode, String context) {
        boolean creating = "create".equals(mode);
        String query = prompt;
        if (creating) {
            for (Map.Entry<String, String> alias : QUERY_ALIASES.entrySet()) {
                if (prompt.contains(alias.getKey())) {
                    query += " " + alias.getValue();
                }
            }
        }
        List<ScoredEntry> matches = new ArrayList<>();
        List<String> targets = MODIFICATION_TARGETS.stream().filter(prompt::contains).toList();
        for (JsonNode entry : getSnapshot().entries()) {
            String type = entry.path("type").asText();
            if ("modify".equals(mode) && !MODIFICATION_TYPES.contains(type)) {
                continue;
            }
            // “预约按钮”修改的是按钮，不能仅因“预约”而检索日期选择或报名表单。
            if ("modify".equals(mode) && !targets.isEmpty()
                    && targets.stream().noneMatch(target -> score(entry, target) > 0)) {
                continue;
            }
            boolean caseEntry = type.endsWith("_case");
            // 局部修改不检索整站案例；新建需求不采用修改示例。
            boolean modificationCase = entry.path("title").asText().contains("修改");
            if (caseEntry && modificationCase != "modify".equals(mode)) {
                continue;
            }
            // 用户本次输入必须有完整关键词命中，背景说明只调整已准入条目的排序。
            int promptScore = score(entry, query);
            if (promptScore > 0) {
                matches.add(new ScoredEntry(entry, 2 * promptScore + score(entry, context)));
            }
        }
        Comparator<ScoredEntry> relevance = Comparator.comparing((ScoredEntry match) ->
                        !"authored_prompt_case".equals(match.entry().path("type").asText()))
                .thenComparing(Comparator.comparingInt(ScoredEntry::score).reversed())
                .thenComparing(match -> match.entry().path("id").asText());
        matches.sort(relevance);
        if (creating) {
            // 使用最相关场景已有的关联关系，单跳补充配色与取舍；不启用待审条目。
            ScoredEntry scene = matches.stream()
                    .filter(match -> "scene".equals(match.entry().path("type").asText())).findFirst().orElse(null);
            if (scene != null) {
                Set<String> relatedIds = new HashSet<>();
                scene.entry().path("relatedIds").forEach(id -> relatedIds.add(id.asText()));
                for (JsonNode entry : getSnapshot().entries()) {
                    if (relatedIds.contains(entry.path("id").asText())
                            && Set.of("palette", "reasoning").contains(entry.path("type").asText())) {
                        matches.removeIf(match -> match.entry().path("id").equals(entry.path("id")));
                        matches.add(new ScoredEntry(entry, scene.score() + 1));
                    }
                }
            }
            matches.sort(Comparator.comparingInt((ScoredEntry match) -> designPriority(match.entry()))
                    .thenComparing(relevance));
        }
        ArrayNode result = objectMapper.createArrayNode();
        int length = 0;
        boolean hasCase = false;
        Set<String> selectedDesignTypes = new HashSet<>();
        for (ScoredEntry match : matches) {
            JsonNode entry = match.entry();
            String type = entry.path("type").asText();
            boolean caseEntry = type.endsWith("_case");
            JsonNode reference = project(entry);
            int entryLength = reference.toString().length();
            if ((caseEntry && hasCase) || length + entryLength > 8000
                    || (creating && DESIGN_TYPES.contains(type) && selectedDesignTypes.contains(type))) {
                continue;
            }
            result.add(reference);
            length += entryLength;
            hasCase |= caseEntry;
            selectedDesignTypes.add(type);
            if (result.size() == 5) {
                break;
            }
        }
        return result;
    }

    private int designPriority(JsonNode entry) {
        return switch (entry.path("type").asText()) {
            case "authored_prompt_case" -> 0;
            case "scene" -> 1;
            case "palette" -> 2;
            case "style" -> 3;
            case "typography" -> 4;
            case "layout" -> 5;
            case "reasoning" -> 6;
            default -> 7;
        };
    }

    private int score(JsonNode entry, String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        Set<String> keywords = new LinkedHashSet<>();
        String title = entry.path("title").asText();
        String topic = title.substring(title.indexOf('｜') + 1);
        for (String part : topic.split("[、与/（）()]")) {
            keywords.add(part.replaceAll("(页面|网站|界面)$", ""));
        }
        for (JsonNode tag : entry.path("tags")) {
            for (String part : tag.asText().split("[、与/（）()]")) {
                // 场景和风格的用途标签不是其主题，如金融看板的“作品集”、三维风格的“电商”。
                if (!TOPIC_TYPES.contains(entry.path("type").asText())
                        || topic.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT))) {
                    keywords.add(part);
                }
            }
        }
        for (String alias : entry.path("searchAliases").asText().split("[,;/、|&]")) {
            keywords.add(alias);
        }
        int score = 0;
        for (String keyword : keywords) {
            String text = keyword.trim().toLowerCase(Locale.ROOT);
            if (text.length() < 2 || STOP_WORDS.contains(text)) {
                continue;
            }
            boolean chinese = text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
            boolean matches = chinese ? normalized.contains(text)
                    : Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(text) + "(?![\\p{L}\\p{N}])")
                    .matcher(normalized).find();
            if (matches) {
                score += topic.toLowerCase(Locale.ROOT).contains(text) ? 12 + Math.min(text.length(), 12) : 6;
            }
        }
        return score;
    }

    /**
     * 首次优化时加载，缺失文件只影响优化功能；成功后复用快照，更新数据后重启生效。
     */
    private synchronized Snapshot getSnapshot() {
        if (snapshot != null) {
            return snapshot;
        }
        try {
            Path directory = Path.of(knowledgeDirectory);
            JsonNode policies = objectMapper.readTree(Files.readString(
                    directory.resolve("固定优化原则.json"), StandardCharsets.UTF_8));
            if (!policies.path("rules").isArray() || policies.path("rules").isEmpty()) {
                throw new IOException("固定优化原则不能为空");
            }
            List<JsonNode> entries = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Set<String> fixedIds = new HashSet<>();
            ArrayNode constraints = objectMapper.createArrayNode();
            try (BufferedReader reader = Files.newBufferedReader(directory.resolve("知识条目.jsonl"),
                    StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode entry = objectMapper.readTree(line);
                    String id = entry.path("id").asText();
                    if (id.isBlank() || !ids.add(id) || entry.path("content").asText().isBlank()) {
                        throw new IOException("知识条目编号重复或缺少必要字段");
                    }
                    if (FIXED_IDS.contains(id) && "fixed_constraint".equals(entry.path("type").asText())) {
                        constraints.add(project(entry));
                        fixedIds.add(id);
                    } else if (entry.path("retrievalEligible").isBoolean()
                            && entry.path("retrievalEligible").booleanValue()
                            && !"fixed_constraint".equals(entry.path("type").asText())
                            && !entry.path("reviewStatus").asText().contains("pending")) {
                        ObjectNode indexed = project(entry);
                        // 别名与关联编号只用于检索，返回模型上下文前再次投影白名单。
                        indexed.set("searchAliases", entry.path("searchAliases"));
                        indexed.set("relatedIds", entry.path("relatedIds"));
                        entries.add(indexed);
                    }
                }
            }
            if (!fixedIds.equals(FIXED_IDS)) {
                throw new IOException("缺少固定范围约束");
            }
            ObjectNode principles = objectMapper.createObjectNode();
            principles.set("rules", policies.get("rules"));
            principles.set("constraints", constraints);
            snapshot = new Snapshot(principles.toString(), List.copyOf(entries));
            log.info("提示词知识库加载完成，可检索条目: {}，固定约束: {}", entries.size(), constraints.size());
            return snapshot;
        } catch (IOException | RuntimeException e) {
            log.error("提示词知识库加载失败，请检查 prompt.optimization.knowledge-directory", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提示词知识库不可用，请联系管理员");
        }
    }

    private ObjectNode project(JsonNode entry) {
        ObjectNode projected = objectMapper.createObjectNode();
        for (String field : CONTEXT_FIELDS) {
            if (entry.has(field)) {
                projected.set(field, entry.get(field));
            }
        }
        return projected;
    }

    private record Snapshot(String principles, List<JsonNode> entries) {
    }

    private record ScoredEntry(JsonNode entry, int score) {
    }
}
