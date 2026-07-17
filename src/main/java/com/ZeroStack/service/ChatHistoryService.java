package com.ZeroStack.service;

import com.ZeroStack.model.dto.chathistory.ChatHistoryQueryRequest;
import com.ZeroStack.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.ZeroStack.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author nijika
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    /**
     * 添加对话历史
     *
     * @param appId       应用id
     * @param message     消息内容
     * @param messageType 消息类型
     * @param userId      用户id
     * @return 是否添加成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用id删除对话历史
     *
     * @param appId 应用id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 将对话历史加载到内存中
     *
     * @param appId      应用id
     * @param chatMemory 对话内存
     * @param maxCount   最大数量
     * @return 加载的数量
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 构造查询条件
     *
     * @param chatHistoryQueryRequest 查询条件
     * @return 查询包装器
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 分页查询应用对话历史
     *
     * @param appId          应用id
     * @param pageSize       每页大小
     * @param lastCreateTime 最后创建时间
     * @param loginUser      登录用户
     * @return 对话历史分页结果
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}
