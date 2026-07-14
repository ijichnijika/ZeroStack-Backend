package com.ZeroStack.core.parser;

import com.ZeroStack.ai.model.MultiFileCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiFileCodeParser 单元测试
 */
class MultiFileCodeParserTest {

    private final MultiFileCodeParser parser = new MultiFileCodeParser();

    @Test
    void parseCode_withAllThreeBlocks() {
        String input = """
                创建完整网页：
                ```html
                <!DOCTYPE html>
                <html>
                <head><title>多文件</title><link rel="stylesheet" href="style.css"></head>
                <body><h1>标题</h1><script src="script.js"></script></body>
                </html>
                ```
                ```css
                h1 { color: blue; text-align: center; }
                ```
                ```js
                console.log('加载完成');
                ```
                完成！
                """;
        MultiFileCodeResult result = parser.parseCode(input);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        assertNotNull(result.getCssCode());
        assertNotNull(result.getJsCode());
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE html>"));
        assertTrue(result.getCssCode().contains("color: blue"));
        assertTrue(result.getJsCode().contains("console.log"));
    }

    @Test
    void parseCode_withJavascriptTag() {
        // ```javascript 全拼也应匹配
        String input = """
                ```html
                <div>内容</div>
                ```
                ```css
                div { margin: 0; }
                ```
                ```javascript
                document.addEventListener('DOMContentLoaded', () => {});
                ```
                """;
        MultiFileCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
        assertNotNull(result.getCssCode());
        assertNotNull(result.getJsCode());
        assertTrue(result.getJsCode().contains("DOMContentLoaded"));
    }

    @Test
    void parseCode_onlyHtml_cssAndJsNull() {
        // 只有 HTML 块，CSS 和 JS 应为 null
        String input = """
                ```html
                <p>仅HTML</p>
                ```
                """;
        MultiFileCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
        assertNull(result.getCssCode());
        assertNull(result.getJsCode());
    }

    @Test
    void parseCode_noCodeBlocks_allNull() {
        // 完全没有代码块
        String input = "这是一段纯文本描述，没有任何代码块";
        MultiFileCodeResult result = parser.parseCode(input);
        assertNotNull(result);
        assertNull(result.getHtmlCode());
        assertNull(result.getCssCode());
        assertNull(result.getJsCode());
    }

    @Test
    void parseCode_caseInsensitive() {
        String input = """
                ```HTML
                <p>大写</p>
                ```
                ```CSS
                p { color: red; }
                ```
                ```JS
                alert(1);
                ```
                """;
        MultiFileCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
        assertNotNull(result.getCssCode());
        assertNotNull(result.getJsCode());
    }

    @Test
    void parseCode_contentTrimmed() {
        String input = """
                ```html
                
                  <div>带空白</div>
                
                ```
                """;
        MultiFileCodeResult result = parser.parseCode(input);
        assertNotNull(result.getHtmlCode());
        // 结果应该被 trim
        assertFalse(result.getHtmlCode().startsWith("\n"));
        assertFalse(result.getHtmlCode().endsWith("\n"));
    }
}
