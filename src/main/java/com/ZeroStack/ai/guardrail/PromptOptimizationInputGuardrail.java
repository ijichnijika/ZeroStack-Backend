package com.ZeroStack.ai.guardrail;

import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.io.IOException;

/**
 * 复用现有注入检测，优化请求命中后直接拒绝，不将拒绝话术当成优化结果。
 */
public class PromptOptimizationInputGuardrail extends PromptSafetyInputGuardrail {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        try {
            JsonNode input = objectMapper.readTree(userMessage.singleText());
            if (input == null || !input.isObject()) {
                return fatal("优化请求格式错误", new BusinessException(ErrorCode.PARAMS_ERROR, "优化请求格式错误"));
            }
            // 只检测用户输入，知识参考不作为用户指令处理。
            String text = input.path("prompt").asText() + "\n" + input.path("context").asText();
            InputGuardrailResult result = super.validate(UserMessage.from(text));
            if (result.successfulText() != null) {
                String message = "需求包含不允许的指令，请仅描述网页需求";
                return fatal(message, new BusinessException(ErrorCode.FORBIDDEN_ERROR, message));
            }
            return result;
        } catch (IOException e) {
            return fatal("优化请求格式错误", new BusinessException(ErrorCode.PARAMS_ERROR, "优化请求格式错误"));
        }
    }
}
