package com.ZeroStack.service.impl;

import com.ZeroStack.ai.AiPromptOptimizationService;
import com.ZeroStack.core.prompt.PromptKnowledgeBase;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.model.dto.prompt.PromptOptimizeRequest;
import com.ZeroStack.model.vo.PromptOptimizationVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptOptimizationServiceImplTest {

    private final PromptOptimizationServiceImpl service = new PromptOptimizationServiceImpl();

    private String lastPrinciples;
    private String lastInput;
    private int modelCalls;

    private PromptOptimizeRequest request;

    @BeforeEach
    void setUp() {
        request = new PromptOptimizeRequest();
        request.setPrompt("做个博客");
        PromptKnowledgeBase knowledgeBase = new PromptKnowledgeBase();
        ReflectionTestUtils.setField(knowledgeBase, "knowledgeDirectory", "../docx/references/RAG知识库");
        ReflectionTestUtils.setField(knowledgeBase, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "promptKnowledgeBase", knowledgeBase);
    }

    @Test
    void validatesInputBeforeCallingModelOrKnowledgeBase() {
        assertThrows(BusinessException.class, () -> service.optimize(null));
        request.setPrompt(" ");
        assertThrows(BusinessException.class, () -> service.optimize(request));
        request.setPrompt("字".repeat(5001));
        assertThrows(BusinessException.class, () -> service.optimize(request));
        request.setPrompt("博客");
        request.setMode("unknown");
        assertThrows(BusinessException.class, () -> service.optimize(request));
        request.setMode("modify");
        request.setContext("字".repeat(2001));
        assertThrows(BusinessException.class, () -> service.optimize(request));
        assertEquals(0, modelCalls);
    }

    @Test
    void sendsStructuredInputAndFixedPrinciplesInOneModelCall() throws Exception {
        request.setPrompt("博客\n\"引用\"");
        PromptOptimizationVO result = result("博客需求", List.of());
        stubModel(result);
        assertSame(result, service.optimize(request));
        assertEquals(1, modelCalls);
        assertTrue(lastPrinciples.contains("POLICY-01"));
        assertTrue(lastPrinciples.contains("SCOPE-01"));
        JsonNode json = new ObjectMapper().readTree(lastInput);
        assertEquals(request.getPrompt(), json.path("prompt").asText());
        assertEquals("create", json.path("mode").asText());
        assertEquals("", json.path("context").asText());
        assertTrue(json.path("references").isArray());
    }

    @Test
    void acceptsClarificationInsteadOfInventingWebsitePurpose() {
        request.setPrompt("做个网站");
        PromptOptimizationVO result = result("", List.of("网站用来做什么？"));
        stubModel(result);
        assertSame(result, service.optimize(request));
    }

    @Test
    void rejectsEmptyAndOversizedModelResults() {
        stubModel(result("", List.of()));
        assertThrows(BusinessException.class, () -> service.optimize(request));
        stubModel(result("字".repeat(5001), List.of()));
        assertThrows(BusinessException.class, () -> service.optimize(request));
    }

    @Test
    void reportsModelFailureInsteadOfReturningOriginalAsSuccess() {
        AiPromptOptimizationService ai = (principles, input) -> {
            throw new IllegalStateException("model unavailable");
        };
        ReflectionTestUtils.setField(service, "aiPromptOptimizationService", ai);
        BusinessException error = assertThrows(BusinessException.class, () -> service.optimize(request));
        assertEquals("提示词优化失败，请稍后重试", error.getMessage());
    }

    private void stubModel(PromptOptimizationVO result) {
        AiPromptOptimizationService ai = (principles, input) -> {
            modelCalls++;
            lastPrinciples = principles;
            lastInput = input;
            return result;
        };
        ReflectionTestUtils.setField(service, "aiPromptOptimizationService", ai);
    }

    private PromptOptimizationVO result(String prompt, List<String> questions) {
        PromptOptimizationVO result = new PromptOptimizationVO();
        result.setOptimizedPrompt(prompt);
        result.setAssumptions(List.of());
        result.setQuestions(questions);
        result.setRequiredDependencies(List.of());
        return result;
    }
}
