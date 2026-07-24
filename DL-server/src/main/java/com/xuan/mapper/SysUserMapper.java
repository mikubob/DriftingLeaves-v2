package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuan.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     * <p>
     * 用于博客端邮箱验证码登录：先按 email 定位 sys_user，再加载 UserDetails。
     * </p>
     */
    SysUser selectByEmail(@Param("email") String email);

    /**
     * 根据第三方平台 + oauthId 查询用户
     * <p>
     * 用于 GitHub/Gitee OAuth2 登录时查找已绑定的本地账号。
     * </p>
     *
     * @param oauthProvider 第三方平台标识（github / gitee）
     * @param oauthId       第三方平台返回的用户 ID
     */
    SysUser selectByOAuth(@Param("oauthProvider") String oauthProvider,
                          @Param("oauthId") String oauthId);

    /**
     * 管理端分页查询用户列表
     * <p>
     * 返回 Map 便于在 Service 层将角色聚合字符串转换为 List。
     * </p>
     *
     * @param page    分页对象
     * @param keyword 用户名/邮箱关键字
     * @param status  用户状态
     * @param role    角色编码过滤
     * @return 分页结果
     */
    IPage<Map<String, Object>> selectUserPage(Page<Map<String, Object>> page,
                                              @Param("keyword") String keyword,
                                              @Param("status") Integer status,
                                              @Param("role") String role);
}
