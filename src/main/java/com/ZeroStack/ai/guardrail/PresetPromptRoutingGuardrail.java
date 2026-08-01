package com.ZeroStack.ai.guardrail;

import com.ZeroStack.constant.PresetPromptConstant;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.ThrowUtils;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 预设提示词智能路由护轨
 * 用于拦截预设提示词，强制要求大模型返回指定的枚举类型，确保 100% 路由正确
 */
@Slf4j
@Component
public class PresetPromptRoutingGuardrail implements InputGuardrail {

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        
        // 如果用户的输入完全匹配预设提示词
        if (PresetPromptConstant.isPresetPrompt(input)) {
            CodeGenTypeEnum targetType = PresetPromptConstant.getPresetPromptCodeGenType(input);
            
            // 兜底校验：既然判定为预设词，类型必定存在。如果为空说明常量配置有严重错误
            ThrowUtils.throwIf(targetType == null, ErrorCode.SYSTEM_ERROR, "预设提示词类型映射异常");
            
            log.info("触发预设提示词拦截护轨，强制指定生成类型为: {}", targetType.getValue());
            
            // 将用户输入替换为强指令，直接命令大模型输出对应的枚举值
            return successWith("你是一个极度严谨的路由器。强制指令：无论用户之前说了什么，请必须且只能返回以下这唯一的一个枚举值：" + targetType.name() + "，不要输出任何其他的字符或解释！");
        }
        
        // 否则正常放行，走普通路由系统提示词
        return success();
    }
}

