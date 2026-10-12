package com.ZeroStack.model.vo;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

@Data
@Description("提示词优化结果，由用户决定是否用于生成网页")
public class PromptOptimizationVO {

    @Description("可直接用于网页生成的中文需求，最多5000字；无法确定用途时返回空字符串并提出问题")
    private String optimizedPrompt;

    @Description("重要默认假设，正文也必须体现这些假设；没有则返回空数组")
    private List<String> assumptions;

    @Description("缺少用途或修改目标等关键信息时需要用户回答的问题；没有则返回空数组")
    private List<String> questions;

    @Description("用户明确要求真实业务能力时所需的外部服务或接口；没有则返回空数组")
    private List<String> requiredDependencies;
}
