package com.ZeroStack.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成应用标题的结果")
@Data
public class AppTitleResult {

    @Description("生成的应用标题，最多12个字符")
    private String title;
}
