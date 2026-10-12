package com.ZeroStack.ai.guardrail;

import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 优化结果使用独立结构校验，不因正常提到“密钥”“证书”而误伤支付需求。
 */
public class PromptOptimizationOutputGuardrail implements OutputGuardrail {

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "\\bsk-[A-Za-z0-9_-]{16,}\\b|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text();
        if (text == null || text.isBlank()) {
            return invalidResult();
        }
        if (SECRET_PATTERN.matcher(text).find()) {
            String message = "优化结果包含疑似凭据，请勿输出实际密钥或私钥";
            return fatal(message, new BusinessException(ErrorCode.FORBIDDEN_ERROR, message));
        }
        try {
            JsonNode result = objectMapper.readTree(text);
            if (result == null || !result.isObject() || !result.path("optimizedPrompt").isTextual()
                    || result.path("optimizedPrompt").asText().length() > 5000) {
                return invalidResult();
            }
            for (String field : List.of("assumptions", "questions", "requiredDependencies")) {
                JsonNode items = result.path(field);
                if (!items.isArray()) {
                    return invalidResult();
                }
                for (JsonNode item : items) {
                    if (!item.isTextual() || item.asText().isBlank()) {
                        return invalidResult();
                    }
                }
            }
            if (result.path("optimizedPrompt").asText().isBlank() && result.path("questions").isEmpty()) {
                return invalidResult();
            }
            return success();
        } catch (IOException e) {
            return invalidResult();
        }
    }

    private OutputGuardrailResult invalidResult() {
        return failure("优化结果格式或长度不符合要求");
    }
}
