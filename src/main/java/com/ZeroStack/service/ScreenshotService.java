package com.ZeroStack.service;

public interface ScreenshotService {
    /**
     *  生成并上传网页截图
     * @param webUrl 网页地址
     * @return 截图的URL
     */
    String generateAndUploadScreenshot(String webUrl);
}
