package com.ZeroStack.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.ZeroStack.exception.BusinessException;
import com.ZeroStack.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * 截图工具类
 */
@UtilityClass
@Slf4j
public class WebScreenshotUtils {

    // TODO: 目前使用 ThreadLocal 可能会导致 Web 容器线程池中的 Chrome 进程长期挂载且占用高内存。后续可考虑引入RabbitMQ，将截图任务抽离为异步消息消费，配合对象池实现完美解耦与限流。
    private static final ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();

    // 记录驱动检查是否已失败，避免无驱动或网络不通时反复发生长耗时重试
    private static volatile boolean driverCheckFailed = false;

    public static WebDriver getDriver() {
        WebDriver driver = driverThreadLocal.get();
        if (driver == null) {
            final int DEFAULT_WIDTH = 1600;
            final int DEFAULT_HEIGHT = 900;
            driver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            if (driver != null) {
                driverThreadLocal.set(driver);
            }
        }
        return driver;
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        try {
            WebDriver currentDriver = getDriver();
            if (currentDriver == null) {
                log.warn("ChromeDriver 未就绪或不可用，跳过网页截图生成");
                return null;
            }
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页
            currentDriver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad(currentDriver);
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) currentDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            // 压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     *
     * @param width  窗口宽度
     * @param height 窗口高度
     */
    private static WebDriver initChromeDriver(int width, int height) {
        if (driverCheckFailed) {
            return null;
        }
        try {
            // 优先检测本地已存在的 chromedriver 路径，避免在线检测由于网络限制超时
            String customDriverPath = System.getProperty("webdriver.chrome.driver");
            if (StrUtil.isBlank(customDriverPath)) {
                customDriverPath = System.getenv("CHROMEDRIVER_PATH");
            }
            if (StrUtil.isBlank(customDriverPath)) {
                String[] commonPaths = {
                        "/usr/local/bin/chromedriver",
                        "/opt/homebrew/bin/chromedriver",
                        System.getProperty("user.dir") + File.separator + "chromedriver"
                };
                for (String path : commonPaths) {
                    if (FileUtil.exist(path) && FileUtil.file(path).canExecute()) {
                        customDriverPath = path;
                        break;
                    }
                }
            }

            if (StrUtil.isNotBlank(customDriverPath)) {
                System.setProperty("webdriver.chrome.driver", customDriverPath);
                log.info("使用本地 ChromeDriver: {}", customDriverPath);
            } else {
                // 无本地驱动时尝试快速探测，限制单次超时，避免阻塞部署主流程
                WebDriverManager.chromedriver().timeout(3).setup();
            }

            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            // 无头模式
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments(
                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            driverCheckFailed = true;
            log.warn("初始化 Chrome 驱动失败，已降级跳过自动截图: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存图片到文件
     *
     * @param imageBytes 图片字节数组
     * @param imagePath  图片保存路径
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     *
     * @param originalImagePath   原始图片路径
     * @param compressedImagePath 压缩后的图片路径
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY);
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     *
     * @param driver WebDriver 实例
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                    .equals("complete"));
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

}
