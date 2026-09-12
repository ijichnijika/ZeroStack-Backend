package com.ZeroStack.exception;

import cn.hutool.json.JSONUtil;
import com.ZeroStack.common.BaseResponse;
import com.ZeroStack.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        // 尝试处理 SSE 请求
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        // 对于普通请求，返回标准 JSON 响应
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        
        // 如果是包装了 BusinessException 的异常（如 LangChain4j Guardrail 抛出的异常）
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof BusinessException) {
                return businessExceptionHandler((BusinessException) cause);
            }
            cause = cause.getCause();
        }

        // 尝试处理 SSE 请求
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理SSE请求的错误响应
     * 
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return true表示是SSE请求并已处理，false表示不是SSE请求
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        if ((accept != null && accept.contains("text/event-stream")) || 
            uri.contains("/chat/gen/code")) {
            if (response.isCommitted()) {
                return true;
            }
            try {
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                Map<String, Object> errorData = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage
                );
                String errorJson = JSONUtil.toJsonStr(errorData);
                String sseData = "event: business-error\ndata: " + errorJson + "\n\n" + "event: done\ndata: {}\n\n";
                byte[] bytes = sseData.getBytes(StandardCharsets.UTF_8);
                response.getOutputStream().write(bytes);
                response.getOutputStream().flush();
                return true;
            } catch (Exception e) {
                log.error("Failed to write SSE error response", e);
                return true;
            }
        }
        return false;
    }
}
