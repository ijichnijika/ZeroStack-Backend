package com.ZeroStack.core.handler;

import com.ZeroStack.model.entity.User;
import com.ZeroStack.model.enums.ChatHistoryMessageTypeEnum;
import com.ZeroStack.service.ChatHistoryOriginalService;
import com.ZeroStack.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Agent 工作流流处理器
 * 处理 CodeGenWorkflow.executeWorkflowWithFlux() 产生的进度消息流。
 */
@Slf4j
public class AgentWorkflowStreamHandler {

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               ChatHistoryOriginalService chatHistoryOriginalService,
                               long appId, User loginUser) {
        StringBuilder markdownBuilder = new StringBuilder();

        return originFlux
                .map(chunk -> {
                    try {
                        if (JSONUtil.isTypeJSON(chunk)) {
                            JSONObject json = JSONUtil.parseObj(chunk);
                            String type = json.getStr("type");
                            if ("workflow_progress".equals(type)) {
                                String stepName = json.getStr("stepName");
                                int stepNumber = json.getInt("stepNumber", 0);
                                String message = json.getStr("message");
                                String icon = stepNumber == -1 ? "❌" : ("完成".equals(stepName) ? "🎉" : (stepNumber == 0 ? "🚀" : "✅"));
                                markdownBuilder.append("\n> ").append(icon).append(" **[").append(stepName).append("]** ").append(message).append("\n\n");
                            } else if ("ai_response".equals(type) || "ai_thinking".equals(type)) {
                                markdownBuilder.append(json.getStr("data", ""));
                            } else {
                                markdownBuilder.append(chunk);
                            }
                        } else {
                            markdownBuilder.append(chunk);
                        }
                    } catch (Exception e) {
                        markdownBuilder.append(chunk);
                    }
                    return chunk;
                })
                .doOnComplete(() -> {
                    markdownBuilder.append("\n\n✅ Agent 已完成代码生成，代码已保存到本地，可通过「部署」按钮预览和部署。");
                    String finalMessage = markdownBuilder.toString();
                    chatHistoryService.addChatMessage(appId, finalMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    chatHistoryOriginalService.addOriginalChatMessage(appId, finalMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    log.info("Agent 工作流完成，已记录完整 Markdown 消息到 ChatHistory，appId={}", appId);
                })
                .doOnError(error -> {
                    markdownBuilder.append("\n\n❌ Agent 代码生成失败: ").append(error.getMessage());
                    String finalMessage = markdownBuilder.toString();
                    chatHistoryService.addChatMessage(appId, finalMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    chatHistoryOriginalService.addOriginalChatMessage(appId, finalMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    log.error("Agent 工作流执行失败，已记录错误消息，appId={}", appId);
                })
                .doFinally(signalType -> {
                    if (signalType == reactor.core.publisher.SignalType.CANCEL) {
                        markdownBuilder.append("\n\n*[已手动停止生成]*");
                        String finalMessage = markdownBuilder.toString();
                        chatHistoryService.addChatMessage(appId, finalMessage,
                                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                        chatHistoryOriginalService.addOriginalChatMessage(appId, finalMessage,
                                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                        log.info("Agent 工作流被手动停止，已记录部分消息到 ChatHistory，appId={}", appId);
                    }
                });
    }
}
