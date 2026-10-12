package com.ZeroStack.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ZeroStack.ai.AiPromptOptimizationService;
import com.ZeroStack.core.prompt.PromptKnowledgeBase;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.ThrowUtils;
import com.ZeroStack.model.dto.prompt.PromptOptimizeRequest;
import com.ZeroStack.model.vo.PromptOptimizationVO;
import com.ZeroStack.service.PromptOptimizationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.guardrail.OutputGuardrailException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    @Resource
    private AiPromptOptimizationService aiPromptOptimizationService;

    @Resource
    private PromptKnowledgeBase promptKnowledgeBase;

    @Override
    public PromptOptimizationVO optimize(PromptOptimizeRequest request) {
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getPrompt()),
                ErrorCode.PARAMS_ERROR, "请输入需要优化的需求");
        ThrowUtils.throwIf(request.getPrompt().length() > 5000, ErrorCode.PARAMS_ERROR, "需求最多支持5000字");
        String mode = StrUtil.blankToDefault(request.getMode(), "create");
        ThrowUtils.throwIf(!"create".equals(mode) && !"modify".equals(mode),
                ErrorCode.PARAMS_ERROR, "mode 仅支持 create 或 modify");
        String context = StrUtil.blankToDefault(request.getContext(), "");
        ThrowUtils.throwIf(context.length() > 2000, ErrorCode.PARAMS_ERROR, "页面说明最多支持2000字");

        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.put("prompt", request.getPrompt());
        input.put("mode", mode);
        input.put("context", context);
        input.set("references", promptKnowledgeBase.retrieve(request.getPrompt(), mode, context));
        PromptOptimizationVO result;
        try {
            String principles = promptKnowledgeBase.getPrinciples();
            try {
                result = aiPromptOptimizationService.optimize(principles, input.toString());
            } catch (OutputGuardrailException e) {
                // 结构错误最多纠正一次；安全拒绝和网络错误不重试，保留完整原始需求。
                if (e.getCause() instanceof BusinessException) {
                    throw e;
                }
                input.put("outputCorrection", "上次输出格式或长度不符合要求。仅返回符合约定的完整JSON，正文最多5000字，三个列表均为字符串数组。");
                result = aiPromptOptimizationService.optimize(principles, input.toString());
            }
        } catch (Exception e) {
            // LangChain4j 会包装护轨异常，保留明确的业务拒绝原因。
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof BusinessException businessException) {
                    throw businessException;
                }
            }
            log.error("提示词优化失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提示词优化失败，请稍后重试");
        }
        // 不将空结果、格式错误或超长输出当成优化成功，也不静默截断需求。
        ThrowUtils.throwIf(result == null || result.getOptimizedPrompt() == null
                        || invalidList(result.getAssumptions()) || invalidList(result.getQuestions())
                        || invalidList(result.getRequiredDependencies()),
                ErrorCode.OPERATION_ERROR, "优化结果不完整，请重试");
        ThrowUtils.throwIf(StrUtil.isBlank(result.getOptimizedPrompt()) && result.getQuestions().isEmpty(),
                ErrorCode.OPERATION_ERROR, "优化结果为空，请重试");
        ThrowUtils.throwIf(result.getOptimizedPrompt().length() > 5000,
                ErrorCode.OPERATION_ERROR, "优化结果过长，请精简需求后重试");
        return result;
    }

    private boolean invalidList(List<String> items) {
        return items == null || items.stream().anyMatch(StrUtil::isBlank);
    }
}
