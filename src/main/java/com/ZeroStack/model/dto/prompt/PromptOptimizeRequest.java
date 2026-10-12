package com.ZeroStack.model.dto.prompt;

import lombok.Data;

import java.io.Serializable;

/**
 * 独立提示词优化请求，不创建应用。
 */
@Data
public class PromptOptimizeRequest implements Serializable {

    private String prompt;

    /**
     * create：新建需求；modify：局部修改。默认 create。
     */
    private String mode;

    /**
     * 可选的已有页面或选中元素说明。
     */
    private String context;

    private static final long serialVersionUID = 1L;
}
