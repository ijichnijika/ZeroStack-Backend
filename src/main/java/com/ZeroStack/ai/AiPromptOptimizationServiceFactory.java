package com.ZeroStack.ai;

import com.ZeroStack.ai.guardrail.PromptOptimizationInputGuardrail;
import com.ZeroStack.ai.guardrail.PromptOptimizationOutputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiPromptOptimizationServiceFactory {

    @Bean
    public AiPromptOptimizationService aiPromptOptimizationService(
            @Qualifier("ChatModelPrototype") ChatModel chatModel) {
        return create(chatModel);
    }

    AiPromptOptimizationService create(ChatModel chatModel) {
        return AiServices.builder(AiPromptOptimizationService.class)
                .chatModel(chatModel)
                .inputGuardrails(new PromptOptimizationInputGuardrail())
                .outputGuardrails(new PromptOptimizationOutputGuardrail())
                .build();
    }
}
