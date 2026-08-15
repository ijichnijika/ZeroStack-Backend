package com.ZeroStack.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ZeroStack.ai.AiCodeGeneratorService;
import com.ZeroStack.ai.AiCodeGeneratorServiceFactory;
import com.ZeroStack.ai.AiTitleGeneratorService;
import com.ZeroStack.ai.AiTitleGeneratorServiceFactory;
import com.ZeroStack.ai.model.AppTitleResult;
import com.ZeroStack.ai.model.HtmlCodeResult;
import com.ZeroStack.ai.model.MultiFileCodeResult;
import com.ZeroStack.ai.model.message.AiResponseMessage;
import com.ZeroStack.ai.model.message.StreamMessage;
import com.ZeroStack.ai.model.message.StreamMessageTypeEnum;
import com.ZeroStack.ai.model.message.ToolExecutedMessage;
import com.ZeroStack.ai.model.message.ToolRequestMessage;
import com.ZeroStack.core.cache.PresetPromptCacheService;
import com.ZeroStack.core.parser.CodeParserExecutor;
import com.ZeroStack.core.saver.CodeFileSaverExecutor;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.ThrowUtils;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    private static final int CACHED_STREAM_CHUNK_SIZE = 256;
    private static final int VUE_STREAM_CHUNK_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 12;
    private static final Duration CACHED_STREAM_DELAY = Duration.ofMillis(5);
    private static final String THINKING_START_TAG = "<think>\n";
    private static final String THINKING_END_TAG = "</think>\n\n";

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiTitleGeneratorServiceFactory aiTitleGeneratorServiceFactory;

    @Resource
    private PresetPromptCacheService presetPromptCacheService;

    /**
     * 统一入口：根据类型生成并保存代码(使用appid)
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用id
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, long appId) {
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "生成类型为空");
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "用户提示词不能为空");

        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,
                codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的生成类型：" + codeGenTypeEnum.getValue());
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式,appid）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用id
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, long appId) {
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "生成类型为空");
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "用户提示词不能为空");

        // 尝试走预设提示词缓存逻辑
        Flux<String> cachedStream = tryGetCachedStream(userMessage, codeGenTypeEnum, appId);
        if (cachedStream != null) {
            return cachedStream;
        }

        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,
                codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId, userMessage);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId, userMessage);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, userMessage, appId);
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的生成类型：" + codeGenTypeEnum.getValue());
        };
    }

    /**
     * 尝试从缓存获取预设提示词的生成流
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码生成类型
     * @param appId       应用id
     * @return 命中缓存并组装好的 Flux 流，未命中则返回 null
     */
    private Flux<String> tryGetCachedStream(String userMessage, CodeGenTypeEnum codeGenType, long appId) {
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            Optional<List<String>> cachedMessages = presetPromptCacheService.restoreVueProject(userMessage, appId);
            if (cachedMessages.isPresent()) {
                return replayCachedVueProject(cachedMessages.get());
            }
            return null;
        }
        Optional<String> cachedCode = presetPromptCacheService.getTextCode(userMessage, codeGenType);
        if (cachedCode.isPresent()) {
            return replayCachedTextCode(cachedCode.get(), codeGenType, appId, userMessage);
        }
        return null;
    }

    /**
     * Vue 模式缓存回放：将紧凑结构化消息平滑切片为打字机流，还原真实大模型生成体验。
     */
    private Flux<String> replayCachedVueProject(List<String> cachedMessages) {
        return Flux.fromIterable(cachedMessages)
                .concatMap(rawMessage -> {
                    try {
                        StreamMessage streamMessage = JSONUtil.toBean(rawMessage, StreamMessage.class);
                        if (StreamMessageTypeEnum.AI_RESPONSE.getValue().equals(streamMessage.getType())) {
                            AiResponseMessage aiMessage = JSONUtil.toBean(rawMessage, AiResponseMessage.class);
                            String text = aiMessage.getData();
                            if (StrUtil.isEmpty(text)) {
                                return Flux.empty();
                            }
                            int chunkCount = (text.length() + VUE_STREAM_CHUNK_SIZE - 1) / VUE_STREAM_CHUNK_SIZE;
                            return Flux.range(0, chunkCount)
                                    .map(index -> {
                                        String chunk = text.substring(index * VUE_STREAM_CHUNK_SIZE,
                                                Math.min(text.length(), (index + 1) * VUE_STREAM_CHUNK_SIZE));
                                        return JSONUtil.toJsonStr(new AiResponseMessage(chunk));
                                    })
                                    .delayElements(CACHED_STREAM_DELAY);
                        }
                    } catch (Exception e) {
                        log.warn("解析 Vue 缓存回放消息异常，降级直出: {}", e.getMessage());
                    }
                    return Flux.just(rawMessage).delayElements(CACHED_STREAM_DELAY);
                });
    }

    /**
     * 文本模式缓存只保存代码正文，因此仍然复用原有解析和保存流程。
     */
    private Flux<String> replayCachedTextCode(String cachedCode, CodeGenTypeEnum codeGenType,
            long appId, String prompt) {
        int chunkCount = (cachedCode.length() + CACHED_STREAM_CHUNK_SIZE - 1) / CACHED_STREAM_CHUNK_SIZE;
        Flux<String> cachedStream = Flux.range(0, chunkCount)
                .map(index -> cachedCode.substring(index * CACHED_STREAM_CHUNK_SIZE,
                        Math.min(cachedCode.length(), (index + 1) * CACHED_STREAM_CHUNK_SIZE)))
                .delayElements(CACHED_STREAM_DELAY);
        return processCodeStream(cachedStream, codeGenType, appId, prompt);
    }

    private final class VueTokenStreamContext {
        private final FluxSink<String> sink;
        private final TokenStreamCapture capture = new TokenStreamCapture();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final String userMessage;
        private final long appId;

        private VueTokenStreamContext(FluxSink<String> sink, String userMessage, long appId) {
            this.sink = sink;
            this.userMessage = userMessage;
            this.appId = appId;
        }

        private void cancel() {
            cancelled.set(true);
        }

        private void emit(Object message) {
            if (cancelled.get()) {
                return;
            }
            String serializedMessage = JSONUtil.toJsonStr(message);
            capture.record(message);
            sink.next(serializedMessage);
        }

        private void emitThinking(String text) {
            if (cancelled.get()) {
                return;
            }
            sink.next(JSONUtil.toJsonStr(new AiResponseMessage(text)));
        }

        private void startThinking() {
            if (capture.thinkingStarted.compareAndSet(false, true)) {
                emitThinking(THINKING_START_TAG);
            }
        }

        private void closeThinking() {
            if (capture.thinkingStarted.compareAndSet(true, false)) {
                emitThinking(THINKING_END_TAG);
            }
        }

        private void complete() {
            closeThinking();
            if (!cancelled.get()) {
                cacheVueProjectIfPreset(userMessage, capture.snapshot(), appId);
                sink.complete();
            }
        }

        private void fail(Throwable error) {
            if (cancelled.get()) {
                sink.complete();
                return;
            }
            log.error("Vue 代码生成流处理失败", error);
            sink.error(error);
        }
    }

    /**
     * 流式事件紧凑采集器：在流式向前端发射 token 的同时，在内存中将连续的文本 token
     * 自动合并节流为完整的消息，避免在 Redis 缓存中存入成千上万个微小碎片 JSON。
     */
    private static final class TokenStreamCapture {
        private final List<String> compactedEvents = Collections.synchronizedList(new ArrayList<>());
        private final StringBuilder textBuffer = new StringBuilder();
        private final AtomicBoolean thinkingStarted = new AtomicBoolean();

        private synchronized void record(Object message) {
            if (message instanceof AiResponseMessage aiMessage) {
                // 连续文本 token 自动追加至 buffer 合并
                if (aiMessage.getData() != null) {
                    textBuffer.append(aiMessage.getData());
                }
            } else {
                // 遇到工具调用等离散事件，先封口并落入当前合并的文本
                flushTextBuffer();
                compactedEvents.add(JSONUtil.toJsonStr(message));
            }
        }

        private synchronized void flushTextBuffer() {
            if (!textBuffer.isEmpty()) {
                compactedEvents.add(JSONUtil.toJsonStr(new AiResponseMessage(textBuffer.toString())));
                textBuffer.setLength(0);
            }
        }

        private synchronized List<String> snapshot() {
            flushTextBuffer();
            return List.copyOf(compactedEvents);
        }
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param userMessage 用户提示词
     * @param appId       应用 ID
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, String userMessage, long appId) {
        return Flux.create(sink -> {
            VueTokenStreamContext context = new VueTokenStreamContext(sink, userMessage, appId);
            sink.onCancel(context::cancel);
            tokenStream.onPartialResponse(partialResponse -> {
                context.closeThinking();
                context.emit(new AiResponseMessage(partialResponse));
            })
                    .onPartialThinking(partialThinking -> {
                        context.startThinking();
                        context.emitThinking(partialThinking.text());
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        context.closeThinking();
                        context.emit(new ToolRequestMessage(toolExecutionRequest));
                    })
                    .onToolExecuted(toolExecution -> context.emit(new ToolExecutedMessage(toolExecution)))
                    .onCompleteResponse(response -> context.complete())
                    .onError(context::fail)
                    .start();
        });
    }

    private void cacheVueProjectIfPreset(String userMessage, List<String> messages, long appId) {
        presetPromptCacheService.putVueProject(userMessage, messages, appId);
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用id
     * @param userMessage 用户提示词（用于缓存判断）
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, long appId,
            String userMessage) {
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream.doOnNext(codeBuilder::append).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                cacheTextCodeIfPreset(userMessage, codeGenType, completeCode);
                log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败", e);
            }
        });
    }

    private void cacheTextCodeIfPreset(String userMessage, CodeGenTypeEnum codeGenType,
            String completeCode) {
        presetPromptCacheService.putTextCode(userMessage, codeGenType, completeCode);
    }

    /**
     * 调用 AI 生成应用标题
     *
     * @param userMessage 用户提示词
     * @return 生成的标题
     */
    public String generateTitle(String userMessage) {
        ThrowUtils.throwIf(StrUtil.isBlank(userMessage), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        AiTitleGeneratorService aiTitleGeneratorService = aiTitleGeneratorServiceFactory.aiTitleGeneratorService();
        AppTitleResult result = aiTitleGeneratorService.generateTitle(userMessage);
        ThrowUtils.throwIf(result == null || StrUtil.isBlank(result.getTitle()), ErrorCode.OPERATION_ERROR,
                "AI 生成标题失败");

        String title = result.getTitle().trim();
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

}
