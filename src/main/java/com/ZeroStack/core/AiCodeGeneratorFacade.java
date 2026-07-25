package com.ZeroStack.core;

import cn.hutool.json.JSONUtil;
import com.ZeroStack.ai.AiCodeGeneratorService;
import com.ZeroStack.ai.AiCodeGeneratorServiceFactory;
import com.ZeroStack.ai.AiTitleGeneratorService;
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
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private AiTitleGeneratorService aiTitleGeneratorService;

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
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,
                codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
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
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            boolean[] thinkingStarted = {false};
            tokenStream.onPartialResponse((String partialResponse) -> {
                if (thinkingStarted[0]) {
                    thinkingStarted[0] = false;
                    AiResponseMessage endMsg = new AiResponseMessage("</think>\n\n");
                    sink.next(JSONUtil.toJsonStr(endMsg));
                }
                AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                sink.next(JSONUtil.toJsonStr(aiResponseMessage));
            })
                    .onPartialThinking((partialThinking) -> {
                        if (!thinkingStarted[0]) {
                            thinkingStarted[0] = true;
                            AiResponseMessage startMsg = new AiResponseMessage("<think>\n");
                            sink.next(JSONUtil.toJsonStr(startMsg));
                        }
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialThinking.text());
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        if (thinkingStarted[0]) {
                            thinkingStarted[0] = false;
                            AiResponseMessage endMsg = new AiResponseMessage("</think>\n\n");
                            sink.next(JSONUtil.toJsonStr(endMsg));
                        }
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (thinkingStarted[0]) {
                            thinkingStarted[0] = false;
                            AiResponseMessage endMsg = new AiResponseMessage("</think>\n\n");
                            sink.next(JSONUtil.toJsonStr(endMsg));
                        }
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
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
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
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
