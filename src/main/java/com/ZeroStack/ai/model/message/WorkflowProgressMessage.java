package com.ZeroStack.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工作流进度消息
 * 用于 Agent 模式下向前端推送工作流各步骤执行进度
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class WorkflowProgressMessage extends StreamMessage {

    /**
     * 当前步骤名称（如 "图片收集"、"代码生成" 等）
     */
    private String stepName;

    /**
     * 步骤序号
     */
    private Integer stepNumber;

    /**
     * 进度描述消息
     */
    private String message;

    public WorkflowProgressMessage(String stepName, Integer stepNumber, String message) {
        super(StreamMessageTypeEnum.WORKFLOW_PROGRESS.getValue());
        this.stepName = stepName;
        this.stepNumber = stepNumber;
        this.message = message;
    }
}
