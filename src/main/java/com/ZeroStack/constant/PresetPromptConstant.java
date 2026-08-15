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
            "帮我生成一个极简风格的个人博客网站，包含首页、文章列表页和文章详情页。首页需要展示最新的5篇文章和个人简介，整体色调以黑白灰为主，支持移动端自适应，排版要清晰舒适，符合现代审美。", CodeGenTypeEnum.MULTI_FILE,
            "创建一个SaaS产品的企业官网，需要有吸引人的首屏，包含产品特性介绍、客户评价轮播图、详细的定价方案（分基础版、专业版、企业版），以及底部的联系我们表单。整体风格专业、现代、有科技感。", CodeGenTypeEnum.MULTI_FILE,
            "开发一个电商后台管理系统的首页数据看板。需要包含今日营业额、新增用户数、订单总数等核心指标统计卡片，以及订单趋势折线图、商品分类占比饼图。界面设计需要专业现代，使用经典的侧边栏加顶部导航布局。", CodeGenTypeEnum.VUE_PROJECT,
            "设计一个暗黑模式的程序员社区交流页面。包含顶部导航栏（支持全局搜索和快捷发布）、左侧边栏（热门话题分类）、主体区域为动态列表（展示帖子标题、摘要、作者头像、点赞数和评论数），风格极客。", CodeGenTypeEnum.MULTI_FILE
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
