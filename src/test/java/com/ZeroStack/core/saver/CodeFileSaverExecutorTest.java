package com.ZeroStack.core.saver;

import com.ZeroStack.ai.model.HtmlCodeResult;
import com.ZeroStack.ai.model.MultiFileCodeResult;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeFileSaverExecutor 单元测试
 */
class CodeFileSaverExecutorTest {

    @Test
    void executeSaver_htmlType() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("<html><body>executor测试</body></html>");

        File dir = CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML);

        assertNotNull(dir);
        assertTrue(dir.exists());
        assertTrue(new File(dir, "index.html").exists());

        // 清理
        new File(dir, "index.html").delete();
        dir.delete();
    }

    @Test
    void executeSaver_multiFileType() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("<html><body>多文件executor</body></html>");
        result.setCssCode("body { padding: 0; }");
        result.setJsCode("console.log('executor');");

        File dir = CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE);

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
}
