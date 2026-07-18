package com.xuan.interceptor;

import com.xuan.auth.security.SecurityUser;
import com.xuan.constant.AdminRoleConstant;
import com.xuan.context.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 将 Spring Security 认证上下文同步到 BaseContext 的过渡拦截器
 * <p>
 * 阶段二接入 Resource Server 后，旧 JwtTokenAdminInterceptor 已停用，
 * 但业务代码仍依赖 BaseContext 获取当前用户 ID 和角色。
 * 本拦截器在 Spring Security 过滤器链之后执行，从 SecurityContext 中解析用户信息并写入 BaseContext，
 * 保证现有业务代码无需改动。阶段三逐步替换 BaseContext 后删除此拦截器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityContextToBaseContextInterceptor implements HandlerInterceptor {

    private final UserDetailsService userDetailsService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SecurityUser user = resolveSecurityUser();
        if (user != null) {
            BaseContext.setCurrentId(user.getUserId());
            BaseContext.setCurrentRole(resolveRole(user));
            log.debug("BaseContext 已同步: userId={}, role={}", user.getUserId(), BaseContext.getCurrentRole());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
        BaseContext.removeCurrentRole();
    }

    /**
     * 从 SecurityContext 解析 SecurityUser
     * 兼容 principal 为 SecurityUser、String（username）或 Jwt 的情况
     */
    private SecurityUser resolveSecurityUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser user) {
            return user;
        }

        String username = null;
        if (principal instanceof String name) {
            username = name;
        } else if (principal instanceof Jwt jwt) {
            username = jwt.getSubject();
        }

        if (username != null) {
            try {
                return (SecurityUser) userDetailsService.loadUserByUsername(username);
            } catch (Exception ex) {
                log.warn("从 UserDetailsService 加载用户失败: {}", username, ex);
            }
        }

        return null;
    }

    /**
     * 根据 SecurityUser 的权限解析旧体系中的角色常量
     */
    private Integer resolveRole(SecurityUser user) {
        boolean isAdmin = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return isAdmin ? AdminRoleConstant.ADMIN : AdminRoleConstant.VISITOR;
    }
}
