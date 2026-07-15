package com.ZeroStack.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建应用请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 初始化Prompt
     */
    private String initPrompt;

    private static final long serialVersionUID = 1L;
}
