package com.ZeroStack.controller;

import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.exception.GlobalExceptionHandler;
import com.ZeroStack.model.entity.User;
import com.ZeroStack.service.impl.AppServiceImpl;
import com.ZeroStack.service.impl.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppChatControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> receivedMessage = new AtomicReference<>();

    @Test
    void acceptsLongChineseBodyAndReturnsContentAndDoneEvents() throws Exception {
        for (boolean agent : new boolean[]{false, true}) {
            String text = "中".repeat(agent ? 20000 : 5000);
            MockMvc mvc = mvc(true);
            var result = mvc.perform(post("/app/chat/gen/code")
                            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                            .content(mapper.writeValueAsString(Map.of("appId", "123", "message", text, "agent", agent))))
                    .andExpect(request().asyncStarted()).andReturn();
            result.getAsyncResult(5000);
            var response = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse();
            assertEquals(text, receivedMessage.get());
            assertTrue(response.getContentAsString().contains("data:"));
            assertTrue(response.getContentAsString().contains("event:done"));
        }
    }

    @Test
    void stillRejectsOversizedMessagesAndUnauthenticatedRequests() throws Exception {
        for (boolean agent : new boolean[]{false, true}) {
            var response = mvc(true).perform(post("/app/chat/gen/code")
                            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                            .content(mapper.writeValueAsString(Map.of("appId", 123, "message", "中".repeat(agent ? 20001 : 5001), "agent", agent))))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(response.contains("business-error"));
            assertTrue(response.contains("40000"));
        }
        var response = mvc(false).perform(post("/app/chat/gen/code")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"appId\":123,\"message\":\"博客\"}"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("40100"));
        assertNull(receivedMessage.get());
    }

    private MockMvc mvc(boolean loggedIn) {
        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "appService", new AppServiceImpl() {
            @Override
            public Flux<String> chatToGenCode(Long appId, String message, User user, Boolean agent) {
                receivedMessage.set(message);
                return Flux.just("first", "second");
            }
        });
        ReflectionTestUtils.setField(controller, "userService", new UserServiceImpl() {
            @Override
            public User getLoginUser(HttpServletRequest request) {
                if (!loggedIn) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
                return new User();
            }
        });
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
