package com.ZeroStack.controller;

import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.GlobalExceptionHandler;
import com.ZeroStack.model.entity.User;
import com.ZeroStack.model.vo.PromptOptimizationVO;
import com.ZeroStack.service.PromptOptimizationService;
import com.ZeroStack.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PromptControllerTest {

    @Test
    void mapsIndependentOptimizationRequestToStandardResponse() throws Exception {
        PromptOptimizationService service = request -> {
            assertEquals("做个博客", request.getPrompt());
            assertEquals("modify", request.getMode());
            assertEquals("已有文章列表", request.getContext());
            PromptOptimizationVO result = new PromptOptimizationVO();
            result.setOptimizedPrompt("仅修改文章列表");
            result.setAssumptions(List.of());
            result.setQuestions(List.of());
            result.setRequiredDependencies(List.of());
            return result;
        };
        mvc(true, service).perform(post("/api/prompt/optimize").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"做个博客","mode":"modify","context":"已有文章列表"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.optimizedPrompt").value("仅修改文章列表"));
    }

    @Test
    void rejectsUnauthenticatedRequestBeforeOptimization() throws Exception {
        PromptOptimizationService service = request -> {
            fail("未登录时不得调用优化服务");
            return null;
        };
        mvc(false, service).perform(post("/prompt/optimize")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"博客\"}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
    }

    private MockMvc mvc(boolean loggedIn, PromptOptimizationService service) {
        PromptController controller = new PromptController();
        ReflectionTestUtils.setField(controller, "promptOptimizationService", service);
        ReflectionTestUtils.setField(controller, "userService", new UserServiceImpl() {
            @Override
            public User getLoginUser(HttpServletRequest request) {
                if (!loggedIn) {
                    throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
                }
                return new User();
            }
        });
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
