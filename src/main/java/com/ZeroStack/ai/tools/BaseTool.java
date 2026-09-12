package com.ZeroStack.ai.tools;

import cn.hutool.json.JSONObject;
import com.ZeroStack.constant.AppConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具基类
 * 定义所有工具的通用接口
 */
public abstract class BaseTool {

    // TODO: 统一接入沙箱路径安全校验，限制文件操作仅在当前应用目录内并防御路径穿越

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    protected String formatSize(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return String.format("%.1f KB", bytes / 1024.0);
    }

    protected Path resolveProjectPath(Long appId, String relativeFilePath) {
        Path path = Paths.get(relativeFilePath);
        if (!path.isAbsolute()) {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
            return projectRoot.resolve(relativeFilePath).normalize();
        }
        return path;
    }
}
