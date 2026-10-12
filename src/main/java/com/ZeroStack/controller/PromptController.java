package com.ZeroStack.controller;

import com.ZeroStack.common.BaseResponse;
import com.ZeroStack.common.ResultUtils;
import com.ZeroStack.model.dto.prompt.PromptOptimizeRequest;
import com.ZeroStack.model.vo.PromptOptimizationVO;
import com.ZeroStack.ratelimiter.annotation.RateLimit;
import com.ZeroStack.ratelimiter.enums.RateLimitType;
import com.ZeroStack.service.PromptOptimizationService;
import com.ZeroStack.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prompt")
public class PromptController {

    @Resource
    private PromptOptimizationService promptOptimizationService;

    @Resource
    private UserService userService;

    @PostMapping("/optimize")
    @RateLimit(key = "prompt_optimize", limitType = RateLimitType.USER, rate = 5, rateInterval = 60,
            message = "提示词优化过于频繁，请稍后再试")
    public BaseResponse<PromptOptimizationVO> optimize(@RequestBody PromptOptimizeRequest optimizeRequest,
                                                     HttpServletRequest request) {
        userService.getLoginUser(request);
        return ResultUtils.success(promptOptimizationService.optimize(optimizeRequest));
    }
}
