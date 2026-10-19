package com.ZeroStack.model.dto.app;

import lombok.Data;

@Data
public class AppChatRequest {
    private Long appId;
    private String message;
    private Boolean agent = false;
}
