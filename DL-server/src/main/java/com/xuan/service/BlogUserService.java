package com.xuan.service;

import com.xuan.dto.RegisterDTO;

/**
 * 博客端用户服务（注册相关）
 */
public interface BlogUserService {

    /**
     * 发送邮箱验证码
     * <p>
     * 校验邮箱格式与频率限制后，生成验证码并异步发送邮件。
     * </p>
     *
     * @param email 邮箱
     */
    void sendEmailCode(String email);

    /**
     * 用户注册
     * <p>
     * 校验用户名/邮箱唯一性 + 邮箱验证码，创建 sys_user 并关联 GUEST 角色。
     * </p>
     *
     * @param registerDTO 注册信息
     */
    void register(RegisterDTO registerDTO);
}
