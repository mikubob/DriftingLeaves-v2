package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xuan.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
@EqualsAndHashCode(callSuper = true)
public class SysUser extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户名/登录账号（同时作为对外展示名）
    private String username;

    // 密码（BCrypt加密）
    private String password;

    // 邮箱
    private String email;

    // 头像URL
    private String avatar;

    // 用户类型：1博客用户 2管理员 3后台游客
    private Integer userType;

    // 状态：1启用 0禁用
    private Integer status;

    // 登录类型：1本地 2GitHub 3Gitee
    private Integer loginType;

    // 第三方平台用户ID
    private String oauthId;

    // 第三方平台标识：github/gitee
    private String oauthProvider;

    // 最后登录时间
    private LocalDateTime lastLoginTime;

    // 最后登录IP
    private String lastLoginIp;

    // 用户名最后修改时间（半个月内只能修改一次）
    private LocalDateTime usernameModifyTime;

    // 头像最后修改时间（一个月内只能修改一次）
    private LocalDateTime avatarModifyTime;

    // 头像最后修改IP（与账号共同判定头像锁定）
    private String lastAvatarModifyIp;

    // 密码最后修改时间（15天冷却）
    private LocalDateTime passwordModifyTime;
}
