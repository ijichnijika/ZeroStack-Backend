package com.ZeroStack.service.impl;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ZeroStack.service.BuildStatusEmitterService;

@Slf4j
@Service
public class BuildStatusEmitterServiceImpl implements BuildStatusEmitterService {

    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    @Override
    public void addEmitter(Long appId, SseEmitter emitter) {
        emitterMap.put(appId, emitter);
        emitter.onCompletion(() -> emitterMap.remove(appId));
        emitter.onTimeout(() -> emitterMap.remove(appId));
        emitter.onError((e) -> emitterMap.remove(appId));
    }

    @Override
    public void sendToEmitter(Long appId, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(appId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data instanceof String ? data : JSONUtil.toJsonStr(data)));
            } catch (IOException e) {
                log.error("向 appId {} 发送 SSE 失败: {}", appId, e.getMessage());
                emitterMap.remove(appId);
            }
        }
    }

    @Override
    public void completeEmitter(Long appId) {
        SseEmitter emitter = emitterMap.get(appId);
        if (emitter != null) {
            emitter.complete();
            emitterMap.remove(appId);
        }
    }
}
