package com.ZeroStack.core;

import cn.hutool.json.JSONUtil;
import com.ZeroStack.ai.AiCodeGeneratorService;
import com.ZeroStack.ai.AiCodeGeneratorServiceFactory;
import com.ZeroStack.ai.AiTitleGeneratorService;
import com.ZeroStack.ai.AiTitleGeneratorServiceFactory;
import com.ZeroStack.ai.model.AppTitleResult;
import com.ZeroStack.ai.model.HtmlCodeResult;
import com.ZeroStack.ai.model.MultiFileCodeResult;
import com.ZeroStack.ai.model.message.AiResponseMessage;
import com.ZeroStack.ai.model.message.ToolExecutedMessage;
import com.ZeroStack.ai.model.message.ToolRequestMessage;
import com.ZeroStack.core.parser.CodeParserExecutor;
import com.ZeroStack.core.saver.CodeFileSaverExecutor;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.ThrowUtils;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import com.ZeroStack.constant.PresetPromptConstant;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiTitleGeneratorServiceFactory aiTitleGeneratorServiceFactory;

    @Resource
    private CacheManager cacheManager;

    /**
     * 统一入口：根据类型生成并保存代码(使用appid)
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用id
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
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
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
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
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "生成类型为空");

        // 尝试走预设提示词缓存逻辑
        Flux<String> cachedStream = tryGetCachedStream(userMessage, codeGenTypeEnum, appId);
        if (cachedStream != null) {
            return cachedStream;
        }

        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenTypeEnum);
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
                TokenStream TokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(TokenStream);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
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
        if (!PresetPromptConstant.isPresetPrompt(userMessage)) {
            return null;
        }
        Cache cache = cacheManager.getCache(PresetPromptConstant.CACHE_NAME);
        String cachedCode = cache != null ? cache.get(userMessage, String.class) : null;

        if (cachedCode == null || cachedCode.trim().isEmpty()) {
            return null;
        }
        
        log.info("命中预设提示词缓存，跳过大模型调用，直接从缓存加载代码");

        // 切割字符串，每 10 个字符一块，模拟流式输出
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < cachedCode.length(); i += 10) {
            chunks.add(cachedCode.substring(i, Math.min(cachedCode.length(), i + 10)));
        }
        Flux<String> cachedStream = Flux.fromIterable(chunks).delayElements(Duration.ofMillis(30));
        return processCodeStream(cachedStream, codeGenType, appId, userMessage);
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            boolean[] thinkingStarted = {false};
            AtomicBoolean isCancelled = new AtomicBoolean(false);
            sink.onCancel(() -> isCancelled.set(true));

            Runnable closeThinking = () -> {
                if (thinkingStarted[0]) {
                    thinkingStarted[0] = false;
                    sink.next(JSONUtil.toJsonStr(new AiResponseMessage("</think>\n\n")));
                }
            };

            Runnable startThinking = () -> {
                if (!thinkingStarted[0]) {
                    thinkingStarted[0] = true;
                    sink.next(JSONUtil.toJsonStr(new AiResponseMessage("<think>\n")));
                }
            };

            tokenStream.onPartialResponse((String partialResponse) -> {
                        if (isCancelled.get()) {
                            // 强制中断当前线程（通常是 OkHttp 的异步 I/O 线程），避免 LangChain4j 在 1.1-beta7 中吞掉异常导致后台持续接收流
                            Thread.currentThread().interrupt();
                            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "STREAM_CANCELLED");
                        }
                        closeThinking.run();
                        sink.next(JSONUtil.toJsonStr(new AiResponseMessage(partialResponse)));
                    })
                    .onPartialThinking((partialThinking) -> {
                        if (isCancelled.get()) {
                            Thread.currentThread().interrupt();
                            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "STREAM_CANCELLED");
                        }
                        startThinking.run();
                        sink.next(JSONUtil.toJsonStr(new AiResponseMessage(partialThinking.text())));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        closeThinking.run();
                        sink.next(JSONUtil.toJsonStr(new ToolRequestMessage(toolExecutionRequest)));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        sink.next(JSONUtil.toJsonStr(new ToolExecutedMessage(toolExecution)));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        closeThinking.run();
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        if (error != null && error.getMessage() != null && error.getMessage().contains("STREAM_CANCELLED")) {
                            sink.complete();
                        } else {
                            error.printStackTrace();
                            sink.error(error);
                        }
                    })
                    .start();
        });
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
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, long appId, String userMessage) {
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream.doOnNext(codeBuilder::append).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 新增缓存逻辑
                if (PresetPromptConstant.isPresetPrompt(userMessage)) {
                    Cache cache = cacheManager.getCache(PresetPromptConstant.CACHE_NAME);
                    if (cache != null) {
                        cache.put(userMessage, completeCode);
                        log.info("预设提示词代码生成完毕，已自动写入缓存");
                    }
                }
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }


    /**
     * 调用 AI 生成应用标题
     *
     * @param userMessage 用户提示词
     * @return 生成的标题
     */
    public String generateTitle(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空");
        }
        AiTitleGeneratorService aiTitleGeneratorService = aiTitleGeneratorServiceFactory.aiTitleGeneratorService();
        AppTitleResult result = aiTitleGeneratorService.generateTitle(userMessage);
        String title = null;
        if (result != null) {
            title = result.getTitle();
        }
        if (title != null) {
            title = title.trim();
            // 防止模型不听话生成过长标题
            if (title.length() > 12) {
                title = title.substring(0, 12);
            }
        }
        return title;
    }

}
