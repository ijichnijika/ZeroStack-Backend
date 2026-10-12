package com.ZeroStack.ratelimiter;

import com.ZeroStack.controller.PromptController;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import com.ZeroStack.model.dto.prompt.PromptOptimizeRequest;
import com.ZeroStack.model.entity.User;
import com.ZeroStack.model.vo.PromptOptimizationVO;
import com.ZeroStack.ratelimiter.aspect.RateLimitAspect;
import com.ZeroStack.service.PromptOptimizationService;
import com.ZeroStack.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PromptOptimizationRateLimitTest {

    @Test
    void realAspectUsesIndependentUserBucketAndRejectsSixthCallBeforeOptimization() {
        AtomicLong userId = new AtomicLong(42);
        AtomicInteger modelCalls = new AtomicInteger();
        Map<String, RRateLimiter> buckets = new HashMap<>();
        RedissonClient redisson = (RedissonClient) Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class}, (proxy, method, args) -> {
                    if ("getRateLimiter".equals(method.getName())) {
                        return buckets.computeIfAbsent((String) args[0], key -> limiter());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        UserServiceImpl users = new UserServiceImpl() {
            @Override
            public User getLoginUser(HttpServletRequest request) {
                User user = new User();
                user.setId(userId.get());
                return user;
            }
        };
        PromptOptimizationService service = request -> {
            modelCalls.incrementAndGet();
            return new PromptOptimizationVO();
        };
        PromptController controller = new PromptController();
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "promptOptimizationService", service);
        RateLimitAspect aspect = new RateLimitAspect();
        ReflectionTestUtils.setField(aspect, "userService", users);
        ReflectionTestUtils.setField(aspect, "redissonClient", redisson);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.addAspect(aspect);
        PromptController proxied = factory.getProxy();
        MockHttpServletRequest http = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(http));
        try {
            PromptOptimizeRequest request = new PromptOptimizeRequest();
            request.setPrompt("做个博客");
            for (int i = 0; i < 5; i++) {
                proxied.optimize(request, http);
            }
            BusinessException error = assertThrows(BusinessException.class, () -> proxied.optimize(request, http));
            assertEquals(ErrorCode.TOO_MANY_REQUEST.getCode(), error.getCode());
            assertEquals(5, modelCalls.get());
            assertTrue(buckets.containsKey("rate_limit:prompt_optimize:user:42"));
            userId.set(43);
            proxied.optimize(request, http);
            assertEquals(6, modelCalls.get());
            assertTrue(buckets.containsKey("rate_limit:prompt_optimize:user:43"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private RRateLimiter limiter() {
        AtomicInteger permits = new AtomicInteger(5);
        return (RRateLimiter) Proxy.newProxyInstance(RRateLimiter.class.getClassLoader(),
                new Class<?>[]{RRateLimiter.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "expire" -> true;
                    case "trySetRate" -> {
                        assertEquals(RateType.OVERALL, args[0]);
                        assertEquals(5, ((Number) args[1]).intValue());
                        assertEquals(60, ((Number) args[2]).intValue());
                        assertEquals(RateIntervalUnit.SECONDS, args[3]);
                        yield true;
                    }
                    case "tryAcquire" -> permits.getAndDecrement() > 0;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
