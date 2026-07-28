package com.ZeroStack.ai;

import com.ZeroStack.ai.guardrail.PromptSafetyInputGuardrail;
import com.ZeroStack.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    /**
     * 创建AI代码生成类型路由服务实例（用户接口专用，含输入护轨）
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        // 动态获取多例的路由 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("ChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .inputGuardrails(new PromptSafetyInputGuardrail())
                .build();
    }

    /**
     * 创建AI代码生成类型路由服务实例（工作流内部专用，不含输入护轨）
     * 工作流内部调用属于受信场景，不需要对输入进行护轨保护
     */
    public AiCodeGenTypeRoutingService createForWorkflow() {
        ChatModel chatModel = SpringContextUtil.getBean("ChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }
}
