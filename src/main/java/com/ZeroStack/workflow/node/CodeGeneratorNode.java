package com.ZeroStack.workflow.node;

import com.ZeroStack.constant.AppConstant;
import com.ZeroStack.core.AiCodeGeneratorFacade;
import com.ZeroStack.workflow.model.QualityResult;
import com.ZeroStack.workflow.state.WorkflowContext;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import com.ZeroStack.utils.SpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点
 */
@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");
            // 获取appid
            Long appId = context.getAppId();
            // 使用增强提示词作为发给 AI 的用户消息(包含原始提示词与报错信息)
            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            // 获取 AI 代码生成外观服务
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtils.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            // 调用流式代码生成
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            // 如果存在外部流消费者，则实时推送到外部（如 Agent 模式下的前端）
            if (context.getStreamMessageConsumer() != null) {
                log.info("CodeGeneratorNode: 发现 streamMessageConsumer，开始实时推送代码流");
                java.util.function.Consumer<String> consumer = context.getStreamMessageConsumer();
                // 为了防止前端 Markdown 渲染器将 HTML 直接作为 DOM 元素隐藏，我们需要包裹 Markdown 代码块
                if (generationType == CodeGenTypeEnum.HTML || generationType == CodeGenTypeEnum.MULTI_FILE) {
                    consumer.accept("```text\n");
                }
                codeStream = codeStream.doOnNext(consumer)
                                       .doOnComplete(() -> {
                                           if (generationType == CodeGenTypeEnum.HTML || generationType == CodeGenTypeEnum.MULTI_FILE) {
                                               consumer.accept("\n```\n");
                                           }
                                       });
            } else {
                log.warn("CodeGeneratorNode: streamMessageConsumer 为空！无法推送流");
            }
            // 同步等待流式输出完成
            codeStream.blockLast(Duration.ofMinutes(10)); // 最多等待 10 分钟
            // 根据类型设置生成目录
            String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            // 更新状态
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        // 检查是否存在质检失败结果
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 直接将错误修复信息作为新的提示词（起到了修改的作用）
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 添加错误列表
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 添加修复建议（如果有）
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }

}

