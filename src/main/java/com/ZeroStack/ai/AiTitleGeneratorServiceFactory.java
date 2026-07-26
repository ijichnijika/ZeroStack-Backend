package com.ZeroStack.ai;

import com.ZeroStack.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiTitleGeneratorServiceFactory {

    /**
     * 注册生成标题的 AI 服务为 Spring Bean
     */
    public AiTitleGeneratorService createAiTitleGeneratorService() {
        // 动态获取多例的路由 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("ChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiTitleGeneratorService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiTitleGeneratorService aiTitleGeneratorService() {
        return createAiTitleGeneratorService();
    }
}
