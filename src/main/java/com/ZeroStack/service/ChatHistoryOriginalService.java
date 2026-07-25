package com.ZeroStack.service;

import com.mybatisflex.core.service.IService;
import com.ZeroStack.model.entity.ChatHistoryOriginal;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 * 原始对话历史 服务层。
 * 为 vue 工程模式恢复对话记忆(包含工具调用信息)
 *
 * @author nijika
 */
public interface ChatHistoryOriginalService extends IService<ChatHistoryOriginal> {

    /**
     * 添加对话历史
     *
     * @param appId       appID
     * @param message     对话内容
     * @param messageType 消息类型
     * @param userId      用户 ID
     * @return true 添加成功，false 添加失败
     */
    boolean addOriginalChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 批量添加对话历史
     *
     * @param chatHistoryOriginalList 对话历史列表
     * @return true 添加成功，false 添加失败
     */
    boolean addOriginalChatMessageBatch(List<ChatHistoryOriginal> chatHistoryOriginalList);

    /**
     * 根据 appId 关联删除对话历史记录
     *
     * @param appId appID
     * @return true 删除成功，false 删除失败
     */
    boolean deleteByAppId(Long appId);

    /**
     * 将 APP 的对话历史加载到缓存中
     *
     * @param appId      appID
     * @param chatMemory 对话记忆
     * @param maxCount   最大条数
     * @return 加载条数
     */
    int loadOriginalChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);
}

