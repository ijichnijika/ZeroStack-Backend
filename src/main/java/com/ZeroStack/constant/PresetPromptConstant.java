package com.ZeroStack.constant;

import cn.hutool.core.util.StrUtil;
import com.ZeroStack.model.enums.CodeGenTypeEnum;
import java.util.Map;

/**
 * 预设提示词常量
 */
public final class PresetPromptConstant {

    private PresetPromptConstant() {
        // 常量类不应被实例化，避免产生无意义对象。
    }

    /**
     * 预设提示词与代码生成类型的映射关系
     */
    public static final Map<String, CodeGenTypeEnum> PRESET_PROMPTS_MAP = Map.of(
            "做一个个人技术博客：首页是文章列表，可以按标签筛选；文章页带目录和代码高亮；再加一个“关于我”页面。整体简洁、适合长时间阅读。", CodeGenTypeEnum.MULTI_FILE,
            "为一名插画师做作品集网站：首页用大图瀑布流展示作品，点击可以放大查看；另有个人简介和联系方式，风格大胆、色彩鲜明。", CodeGenTypeEnum.MULTI_FILE,
            "做一个电商运营数据看板：顶部显示今日销售额、订单数、访客数，下面是近 30 天销售趋势图和热销商品排行，信息密度高但清楚。", CodeGenTypeEnum.VUE_PROJECT,
            "为一款降噪耳机做产品落地页：首屏是产品大图和购买按钮，往下依次是三个卖点、参数表、用户评价和常见问题。", CodeGenTypeEnum.HTML,
            "做一个待办清单应用：可以添加、完成、删除任务，按“今天 / 本周 / 全部”筛选，数据保存在浏览器本地。", CodeGenTypeEnum.HTML
    );

    /**
     * 预设提示词缓存键前缀
     */
    public static final String CACHE_NAME = "preset_prompts";

    /**
     * 规范化用户输入，保证判断、读写缓存使用同一个键。
     *
     * @param prompt 原始提示词
     * @return 去除首尾空白后的提示词；空输入返回 null
     */
    public static String normalizePrompt(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return null;
        }
        return prompt.trim();
    }

    /**
     * 判断是否为预设提示词
     *
     * @param prompt 提示词
     * @return 是否为预设提示词
     */
    public static boolean isPresetPrompt(String prompt) {
        String normalizedPrompt = normalizePrompt(prompt);
        return normalizedPrompt != null && PRESET_PROMPTS_MAP.containsKey(normalizedPrompt);
    }

    /**
     * 获取预设提示词对应的代码生成类型
     *
     * @param prompt 提示词
     * @return 对应的生成类型，如果不是预设提示词则返回 null
     */
    public static CodeGenTypeEnum getPresetPromptCodeGenType(String prompt) {
        String normalizedPrompt = normalizePrompt(prompt);
        return normalizedPrompt == null ? null : PRESET_PROMPTS_MAP.get(normalizedPrompt);
    }
}
