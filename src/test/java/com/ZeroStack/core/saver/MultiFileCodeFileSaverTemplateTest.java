package com.ZeroStack.core.saver;

import com.ZeroStack.ai.model.MultiFileCodeResult;
import com.ZeroStack.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiFileCodeFileSaverTemplate 模板方法模式 单元测试
 */
class MultiFileCodeFileSaverTemplateTest {

    private final MultiFileCodeFileSaverTemplate saver = new MultiFileCodeFileSaverTemplate();

    @Test
    void saveCode_allFiles() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("<html><body>多文件测试</body></html>");
        result.setCssCode("body { margin: 0; }");
        result.setJsCode("console.log('ok');");

        File dir = saver.saveCode(result);

        assertNotNull(dir);
        assertTrue(dir.exists());
        assertTrue(new File(dir, "index.html").exists());
        assertTrue(new File(dir, "style.css").exists());
        assertTrue(new File(dir, "script.js").exists());

        // 清理
        new File(dir, "index.html").delete();
        new File(dir, "style.css").delete();
        new File(dir, "script.js").delete();
        dir.delete();
    }

    @Test
    void saveCode_onlyHtml_cssAndJsSkipped() {
        // CSS 和 JS 为 null 时不应报错，只是不创建对应文件
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("<html><body>仅HTML</body></html>");

        File dir = saver.saveCode(result);

        assertNotNull(dir);
        assertTrue(new File(dir, "index.html").exists());
        assertFalse(new File(dir, "style.css").exists());
        assertFalse(new File(dir, "script.js").exists());

        // 清理
        new File(dir, "index.html").delete();
        dir.delete();
    }

    @Test
    void saveCode_nullResult_throwsException() {
        assertThrows(BusinessException.class, () -> saver.saveCode(null));
    }

    @Test
    void saveCode_emptyHtmlCode_throwsException() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("");
        assertThrows(BusinessException.class, () -> saver.saveCode(result));
    }

    @Test
    void saveCode_nullHtmlCode_throwsException() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // htmlCode 默认 null，但 CSS/JS 有值
        result.setCssCode("body {}");
        result.setJsCode("alert(1);");
        assertThrows(BusinessException.class, () -> saver.saveCode(result));
    }
}
