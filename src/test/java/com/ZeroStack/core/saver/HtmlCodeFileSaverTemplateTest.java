package com.ZeroStack.core.saver;

import com.ZeroStack.ai.model.HtmlCodeResult;
import com.ZeroStack.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlCodeFileSaverTemplate 模板方法模式 单元测试
 */
class HtmlCodeFileSaverTemplateTest {

    private final HtmlCodeFileSaverTemplate saver = new HtmlCodeFileSaverTemplate();

    @Test
    void saveCode_validResult() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("<html><body><h1>测试</h1></body></html>");

        File dir = saver.saveCode(result);

        assertNotNull(dir);
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        // 验证 index.html 存在
        File htmlFile = new File(dir, "index.html");
        assertTrue(htmlFile.exists());
        assertTrue(htmlFile.length() > 0);

        // 清理
        htmlFile.delete();
        dir.delete();
    }

    @Test
    void saveCode_nullResult_throwsException() {
        assertThrows(BusinessException.class, () -> saver.saveCode(null));
    }

    @Test
    void saveCode_emptyHtmlCode_throwsException() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("");
        assertThrows(BusinessException.class, () -> saver.saveCode(result));
    }

    @Test
    void saveCode_blankHtmlCode_throwsException() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("   ");
        assertThrows(BusinessException.class, () -> saver.saveCode(result));
    }

    @Test
    void saveCode_nullHtmlCode_throwsException() {
        HtmlCodeResult result = new HtmlCodeResult();
        // htmlCode 默认 null
        assertThrows(BusinessException.class, () -> saver.saveCode(result));
    }
}
