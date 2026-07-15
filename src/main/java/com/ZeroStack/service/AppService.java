package com.ZeroStack.service;

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
     *
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);
    /**
     * 获取脱敏后应用信息
     * @param app 应用
     * @return 应用信息
     */
    AppVO getAppVO(App app);

    /**
     * 获取脱敏后应用列表
     * @param appList 应用列表
     * @return 应用列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     * @param appQueryRequest 应用查询请求
     * @return 应用查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
}
