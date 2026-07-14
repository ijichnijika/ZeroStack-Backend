package com.ZeroStack.core.parser;

import com.ZeroStack.ai.model.HtmlCodeResult;
import com.ZeroStack.ai.model.MultiFileCodeResult;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeParserExecutor 单元测试
 */
class CodeParserExecutorTest {

    private static final String HTML_INPUT = """
            ```html
            <p>测试</p>
            ```
            """;

    private static final String MULTI_FILE_INPUT = """
            ```html
            <div>多文件</div>
            ```
            ```css
            div { color: red; }
            ```
            ```js
            console.log('test');
            ```
            """;

    @Test
    void executeParser_htmlType() {
        Object result = CodeParserExecutor.executeParser(HTML_INPUT, CodeGenTypeEnum.HTML);
        assertNotNull(result);
        assertInstanceOf(HtmlCodeResult.class, result);
        HtmlCodeResult htmlResult = (HtmlCodeResult) result;
        assertTrue(htmlResult.getHtmlCode().contains("<p>测试</p>"));
    }

    @Test
    void executeParser_multiFileType() {
        Object result = CodeParserExecutor.executeParser(MULTI_FILE_INPUT, CodeGenTypeEnum.MULTI_FILE);
        assertNotNull(result);
        assertInstanceOf(MultiFileCodeResult.class, result);
        MultiFileCodeResult multiResult = (MultiFileCodeResult) result;
        assertNotNull(multiResult.getHtmlCode());
        assertNotNull(multiResult.getCssCode());
        assertNotNull(multiResult.getJsCode());
    }
}
