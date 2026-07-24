package com.ZeroStack.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiTitleGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    /**
     * 注册生成标题的 AI 服务为 Spring Bean
     */
    @Bean
    public AiTitleGeneratorService aiTitleGeneratorService() {
        return AiServices.builder(AiTitleGeneratorService.class)
                .chatModel(chatModel)
                .build();
    }
}
