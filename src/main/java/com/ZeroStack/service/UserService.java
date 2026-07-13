package com.ZeroStack.service;

import com.ZeroStack.model.dto.user.UserQueryRequest;
import com.ZeroStack.model.vo.LoginUserVO;
import com.ZeroStack.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.ZeroStack.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author nijika>
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);
    /**
     * 获取脱敏的已登录用户信息
     * @param user 已登录用户
     * @return 脱敏的已登录用户信息
     */
    LoginUserVO getLoginUserVO(User user);
    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request 请求
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);
    /**
     * 获取当前登录用户
     *
     * @param request   请求
     * @return  当前登录用户
     */
    User getLoginUser(HttpServletRequest request);
    /**
     * 用户注销
     *
     * @param request   请求
     * @return  是否注销成功
     */
    boolean userLogout(HttpServletRequest request);
    /**
     * 获取脱敏后用户信息
     * @param user 用户
     * @return 用户信息
      */
    UserVO getUserVO(User user);
    /**
     * 获取脱敏后用户列表
     * @param userList 用户列表
     * @return 用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);
    /**
     * 获取用户查询条件
     * @param userQueryRequest 用户查询请求
     * @return 用户查询条件
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
    /**
     * 加密
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);
}
