package com.ZeroStack.core.parser;

import com.ZeroStack.ai.model.HtmlCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlCodeParser 单元测试
 */
class HtmlCodeParserTest {

    private final HtmlCodeParser parser = new HtmlCodeParser();

    @Test
    void parseCode_withValidCodeBlock() {
        String input = """
                这是一段描述文字
                ```html
                <!DOCTYPE html>
                <html>
                <head><title>测试</title></head>
                <body><h1>Hello</h1></body>
                </html>
                ```
                这是结尾描述
                """;
        HtmlCodeResult result = parser.parseCode(input);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE html>"));
        assertTrue(result.getHtmlCode().contains("<h1>Hello</h1>"));
        // 确保描述文字没有混入
        assertFalse(result.getHtmlCode().contains("这是一段描述文字"));
    }

    @Test
    void parseCode_withoutCodeBlock_fallbackToFullContent() {
        // 没有代码块围栏时，应回退为将整段内容作为 HTML
        String input = """
                <html>
                <body><p>纯HTML无围栏</p></body>
                </html>
                """;
        HtmlCodeResult result = parser.parseCode(input);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        assertTrue(result.getHtmlCode().contains("<p>纯HTML无围栏</p>"));
    }

    @Test
    void parseCode_caseInsensitive() {
        // ```HTML 大写也应匹配
        String input = """
                ```HTML
                <div>大写标记</div>
                ```
                """;
        HtmlCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
        assertTrue(result.getHtmlCode().contains("<div>大写标记</div>"));
    }

    @Test
    void parseCode_multipleBlocks_extractsFirst() {
        // 多个 html 代码块时应提取第一个
        String input = """
                ```html
                <p>第一个块</p>
                ```
                ```html
                <p>第二个块</p>
                ```
                """;
        HtmlCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
        assertTrue(result.getHtmlCode().contains("第一个块"));
    }

    @Test
    void parseCode_emptyCodeBlock_fallbackToFullContent() {
        // 空代码块应回退
        String input = """
                ```html
                ```
                """;
        HtmlCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
    }
}
