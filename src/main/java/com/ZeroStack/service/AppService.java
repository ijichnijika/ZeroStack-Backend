package com.ZeroStack.service;

import com.ZeroStack.ai.tools.BaseTool;
import com.ZeroStack.model.dto.app.AppAddRequest;
import com.ZeroStack.model.dto.app.AppQueryRequest;
import com.ZeroStack.model.entity.User;
import com.ZeroStack.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.ZeroStack.model.entity.App;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author nijika
 */
public interface AppService extends IService<App> {
    /**
     * 获取流式生成的应用
     *
     * @param appId     应用id
     * @param message   消息
     * @param loginUser 登录用户
     * @param agent    是否启用agent模式
     * @return 流式生成的应用
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser, Boolean agent);

    /**
     * 停止正在生成的 AI 代码流
     *
     * @param appId     应用id
     * @param loginUser 登录用户
     */
    void stopGenerate(Long appId, User loginUser);

    /**
     * 生成应用标题并更新数据库
     *
     * @param appId     应用id
     * @param prompt    提示词
     * @param loginUser 登录用户
     * @return 生成的标题
     */
    String genAppTitle(Long appId, String prompt, User loginUser);

    /**
     * 部署网页应用
     *
     * @param appId     应用id
     * @param loginUser 登录用户
     * @return 部署结果
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 创建应用
     *
     * @param appAddRequest 应用添加请求
     * @param loginUser     登录用户
     * @return 应用id
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 获取脱敏后应用信息
     *
     * @param app 应用
     * @return 应用信息
     */
    AppVO getAppVO(App app);

    /**
     * 获取脱敏后应用列表
     *
     * @param appList 应用列表
     * @return 应用列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest 应用查询请求
     * @return 应用查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
}
