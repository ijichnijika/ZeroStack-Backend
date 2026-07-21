package com.ZeroStack.ai;

import com.ZeroStack.ai.model.AppTitleResult;
import dev.langchain4j.service.SystemMessage;

/**
 * 专门用于生成应用标题的纯无状态 AI 服务
 */
public interface AiTitleGeneratorService {

    /**
     * 生成应用标题
     *
     * @param userMessage 用户消息
     * @return 生成的标题结果
     */
    @SystemMessage(fromResource = "prompt/codegen-title-system-prompt.txt")
    AppTitleResult generateTitle(String userMessage);
}
