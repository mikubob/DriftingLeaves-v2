package com.xuan.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 上下文工具类
 * <p>
 * 统一封装 {@link SecurityContextHolder} 中的角色判断逻辑，避免在多个 Controller 中重复编写。
 * </p>
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 判断当前认证用户是否拥有 AUDITOR 角色
     *
     * @return true=当前用户为 AUDITOR 角色
     */
    public static boolean hasAuditorRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_AUDITOR".equals(a.getAuthority()));
    }
}
