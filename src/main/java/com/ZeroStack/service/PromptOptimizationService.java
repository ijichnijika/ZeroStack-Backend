package com.ZeroStack.service;

import com.ZeroStack.model.dto.prompt.PromptOptimizeRequest;
import com.ZeroStack.model.vo.PromptOptimizationVO;

public interface PromptOptimizationService {

    PromptOptimizationVO optimize(PromptOptimizeRequest request);
}
