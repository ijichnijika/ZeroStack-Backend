package com.ZeroStack.core.prompt;

import com.ZeroStack.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PromptKnowledgeBaseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path directory;

    @Test
    void retrievesCurrentKnowledgeWithoutRawFieldsOrCandidates() throws Exception {
        Path source = Path.of("../docx/references/RAG知识库");
        PromptKnowledgeBase knowledgeBase = knowledgeBase(source);
        JsonNode references = knowledgeBase.retrieve("做个订单管理后台", "create", "");
        // SCENE-04 没有标签，也应通过标题和正文命中。
        assertTrue(references.toString().contains("SCENE-04"));
        assertTrue(references.size() <= 5);
        Set<String> eligibleIds = new HashSet<>();
        for (String line : Files.readAllLines(source.resolve("知识条目.jsonl"))) {
            JsonNode entry = mapper.readTree(line);
            if (entry.path("retrievalEligible").asBoolean()) {
                eligibleIds.add(entry.path("id").asText());
            }
        }
        assertFalse(eligibleIds.isEmpty());
        for (JsonNode reference : references) {
            assertTrue(eligibleIds.contains(reference.path("id").asText()));
            assertFalse(reference.has("rawFields"));
            assertFalse(reference.has("technicalFields"));
            assertTrue(reference.has("boundary"));
            assertTrue(reference.has("sourceUrls"));
        }
        assertTrue(knowledgeBase.getPrinciples().contains("POLICY-01"));
        assertTrue(knowledgeBase.getPrinciples().contains("SCOPE-01"));
        assertTrue(knowledgeBase.getPrinciples().contains("SCOPE-02"));
    }

    @Test
    void modificationDoesNotRetrieveWholeSiteCases() {
        PromptKnowledgeBase knowledgeBase = knowledgeBase(Path.of("../docx/references/RAG知识库"));
        JsonNode references = knowledgeBase.retrieve("预约按钮改成蓝色", "modify", "咖啡店展示网站");
        assertTrue(references.toString().contains("SCENE-05"));
        assertFalse(references.toString().contains("PATTERN-10"));
        assertFalse(references.toString().contains("UX-01"));
        for (JsonNode entry : references) {
            if ("authored_prompt_case".equals(entry.path("type").asText())) {
                assertEquals("SCENE-05", entry.path("id").asText());
            }
        }
    }

    @Test
    void buttonChangeDoesNotRetrieveSiteScenesOrPalettes() {
        PromptKnowledgeBase knowledgeBase = knowledgeBase(Path.of("../docx/references/RAG知识库"));
        JsonNode references = knowledgeBase.retrieve("只把预约按钮改成蓝色，其他不动", "modify",
                "预约区域只有一个预约按钮，保持文字和点击行为");
        assertTrue(references.toString().contains("SCENE-05"));
        for (JsonNode entry : references) {
            assertFalse(Set.of("scene", "reasoning", "palette", "style", "layout")
                    .contains(entry.path("type").asText()));
        }
    }

    @Test
    void onlinePaymentDoesNotMatchUnrelatedCoursePalette() {
        PromptKnowledgeBase knowledgeBase = knowledgeBase(Path.of("../docx/references/RAG知识库"));
        JsonNode references = knowledgeBase.retrieve(
                "做一个咖啡豆售卖网站，必须接入真实在线支付，不要支付演示", "create", "");
        // 咖啡豆售卖与咖啡店展示并非同一用途，不能仅靠“咖啡”二字套展示案例。
        assertFalse(references.toString().contains("SCENE-01"));
        assertFalse(references.toString().contains("UUPM-COLORS-043"));
        assertFalse(references.toString().contains("UUPM-LANDING-003"));
    }

    @Test
    void coursePaletteStillMatchesExplicitCourseTopic() {
        PromptKnowledgeBase knowledgeBase = knowledgeBase(Path.of("../docx/references/RAG知识库"));
        JsonNode references = knowledgeBase.retrieve("做一个在线课程网站，推荐配色", "create", "");
        assertTrue(references.toString().contains("UUPM-COLORS-043"));
    }

    @Test
    void bodyFragmentsAndBroadContextCannotAdmitUnrelatedKnowledge() throws Exception {
        writeFixture("""
                {"id":"course","type":"palette","title":"配色｜在线课程","content":"真实在线支付咖啡商品","tags":["配色","在线课程"],"retrievalEligible":true}
                {"id":"button","type":"rule","title":"按钮反馈","content":"点击状态","tags":["按钮"],"retrievalEligible":true}
                {"id":"form","type":"rule","title":"表单校验","content":"按钮咖啡","tags":["表单"],"retrievalEligible":true}
                """);
        PromptKnowledgeBase knowledgeBase = knowledgeBase(directory);
        assertTrue(knowledgeBase.retrieve("真实在线支付咖啡商品", "create", "在线课程").isEmpty());
        JsonNode references = knowledgeBase.retrieve("按钮改蓝色", "modify", "已有页面有表单校验");
        assertEquals(1, references.size());
        assertEquals("button", references.get(0).path("id").asText());
    }

    @Test
    void searchAliasesMatchWholeWordsAndDoNotLeakIntoContext() throws Exception {
        writeFixture("""
                {"id":"cafe","type":"scene","title":"场景｜咖啡馆","content":"展示菜单","searchAliases":"Bakery/Cafe","retrievalEligible":true}
                """);
        PromptKnowledgeBase knowledgeBase = knowledgeBase(directory);
        JsonNode references = knowledgeBase.retrieve("Create a cafe website", "create", "");
        assertEquals(1, references.size());
        assertFalse(references.get(0).has("searchAliases"));
        assertTrue(knowledgeBase.retrieve("Create a cafeteria website", "create", "").isEmpty());
    }

    @Test
    void noRelevantKnowledgeStillCarriesFixedPrinciples() {
        PromptKnowledgeBase knowledgeBase = knowledgeBase(Path.of("../docx/references/RAG知识库"));
        assertTrue(knowledgeBase.retrieve("做个网站", "create", "").isEmpty());
        assertTrue(knowledgeBase.getPrinciples().contains("POLICY-05"));
    }

    @Test
    void filtersDisabledAndPendingEntriesBeforeRetrieval() throws Exception {
        writeFixture("""
                {"id":"allowed","type":"rule","title":"咖啡菜单","content":"咖啡菜单可读","retrievalEligible":true,"rawFields":{"secret":"RAW_SENTINEL"},"technicalFields":{"code":"TECH_SENTINEL"}}
                {"id":"disabled","title":"咖啡菜单","content":"DISABLED_SENTINEL","retrievalEligible":false}
                {"id":"pending","title":"咖啡菜单","content":"PENDING_SENTINEL","retrievalEligible":true,"reviewStatus":"revised_pending_recheck"}
                """);
        PromptKnowledgeBase knowledgeBase = knowledgeBase(directory);
        String references = knowledgeBase.retrieve("咖啡菜单", "create", "").toString();
        assertTrue(references.contains("allowed"));
        assertFalse(references.contains("SENTINEL"));
        assertFalse(references.contains("SCOPE-"));
        assertFalse(knowledgeBase.getPrinciples().contains("FIXED_RAW_SENTINEL"));
    }

    @Test
    void rejectsDuplicateIdsAndMissingConstraints() throws Exception {
        writeFixture("{\"id\":\"SCOPE-01\",\"content\":\"重复\"}\n");
        assertThrows(BusinessException.class, () -> knowledgeBase(directory).getPrinciples());
        Files.writeString(directory.resolve("知识条目.jsonl"), "{\"id\":\"only\",\"content\":\"正文\"}\n");
        assertThrows(BusinessException.class, () -> knowledgeBase(directory).getPrinciples());
    }

    private PromptKnowledgeBase knowledgeBase(Path path) {
        PromptKnowledgeBase knowledgeBase = new PromptKnowledgeBase();
        ReflectionTestUtils.setField(knowledgeBase, "knowledgeDirectory", path.toString());
        ReflectionTestUtils.setField(knowledgeBase, "objectMapper", mapper);
        return knowledgeBase;
    }

    private void writeFixture(String entries) throws Exception {
        Files.writeString(directory.resolve("固定优化原则.json"), "{\"rules\":[{\"id\":\"POLICY-01\",\"content\":\"保留用户要求\"}]}");
        Files.writeString(directory.resolve("知识条目.jsonl"), """
                {"id":"SCOPE-01","type":"fixed_constraint","content":"能力边界","retrievalEligible":false,"rawFields":{"secret":"FIXED_RAW_SENTINEL"}}
                {"id":"SCOPE-02","type":"fixed_constraint","content":"局部修改","retrievalEligible":false}
                """ + entries);
    }
}
