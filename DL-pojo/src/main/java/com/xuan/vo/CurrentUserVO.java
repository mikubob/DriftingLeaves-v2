package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息 VO
 * <p>
 * 供 {@code GET /admin/me} 和 {@code GET /blog/auth/me} 接口返回使用。
 * 字段从 JWT claims 与 sys_user 表组装。
 * </p>
 *
 * <h3>roles 字段格式约定(决策 9)</h3>
 * <p>
 * 返回 <b>不带 {@code ROLE_} 前缀</b>的角色名(如 {@code ["ADMIN", "AUTHOR"]}),
 * 与 {@code @PreAuthorize("hasRole('ADMIN')")} 中的写法一致,
 * 前端可直接用于路由守卫与菜单过滤,无需二次处理。
 * </p>
 *
 * @author xuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名(登录账号)
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 角色列表(不带 ROLE_ 前缀)
     */
    private List<String> roles;
}
