package com.ZeroStack.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 应用构建状态推送服务
 * 用于通过 SSE (Server-Sent Events) 向前端实时推送构建进度
 */
public interface BuildStatusEmitterService {

    /**
     * 添加应用的 SSE Emitter
     *
     * @param appId 应用ID
     * @param emitter SSE Emitter实例
     */
    void addEmitter(Long appId, SseEmitter emitter);

    /**
     * 向指定应用发送 SSE 事件
     *
     * @param appId 应用ID
     * @param eventName 事件名称（如 status, done）
     * @param data 事件数据内容
     */
    void sendToEmitter(Long appId, String eventName, Object data);

    /**
     * 结束推送并清理 Emitter
     *
     * @param appId 应用ID
     */
    void completeEmitter(Long appId);
}
