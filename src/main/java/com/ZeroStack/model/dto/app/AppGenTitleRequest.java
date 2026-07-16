package com.ZeroStack.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 生成应用标题请求
 */
@Data
public class AppGenTitleRequest implements Serializable {

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 用户提示词 prompt
     */
    private String prompt;

    private static final long serialVersionUID = 1L;
}
