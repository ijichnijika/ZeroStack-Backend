package com.ZeroStack.ai;

import com.ZeroStack.model.vo.PromptOptimizationVO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 无工具、无聊天记忆的提示词优化服务。
 */
public interface AiPromptOptimizationService {

    @SystemMessage(fromResource = "prompt/prompt-optimization-system-prompt.txt")
    PromptOptimizationVO optimize(@V("principles") String principles, @UserMessage String userMessage);
}
