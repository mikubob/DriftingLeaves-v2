package com.xuan.auth.config;

import com.xuan.auth.security.SecurityUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT Token 自定义配置
 * <p>
 * 在 SAS 颁发 access_token 时，向 JWT claims 注入业务所需的自定义字段：
 * </p>
 * <ul>
 *     <li>{@code roles}：用户角色列表（如 {@code ["ROLE_ADMIN", "ROLE_AUTHOR"]}），供 Resource Server 解析权限</li>
 *     <li>{@code user_id}：用户 ID（Long），供业务代码获取当前用户标识（如操作日志记录）</li>
 *     <li>{@code username}：用户名，便于前端直接展示，无需再查库</li>
 * </ul>
 *
 * <h3>为何要把 user_id 写入 JWT？</h3>
 * <p>
 * 业务代码（如 OperationLogAspect）需要从 SecurityContextHolder 获取当前用户 ID。
 * 但 Resource Server 解析 JWT 后，SecurityContext 中的 principal 是
 * {@code Jwt} 对象（而非 SecurityUser），无法直接调用 {@code getUserId()}。
 * 将 user_id 写入 JWT claims 后，业务代码可通过 {@code jwt.getClaim("user_id")} 获取，
 * 避免每次请求都查库。
 * </p>
 *
 * <h3>调用链路</h3>
 * <pre>
 * 1. AdminPasswordCodeAuthenticationProvider.authenticate()
 *    └─ 创建 authenticatedToken，principal = SecurityUser
 *    └─ 提取 userId/username/roles 写入 JWT claims
 * 3. JwtCustomizerConfig.jwtCustomizer() 回调
 *    └─ context.getPrincipal() = authenticatedToken
 *    └─ authenticatedToken.getPrincipal() = SecurityUser
 *    └─ 提取 userId/username/roles 写入 JWT claims
 * 4. JwtGenerator 生成最终 JWT
 * </pre>
 *
 * @author xuan
 */
@Configuration
public class JwtCustomizerConfig {

    /**
     * JWT 自定义器 Bean
     * <p>
     * 仅对 access_token 类型生效（refresh_token 不含这些 claims）。
     * </p>
     *
     * @return OAuth2TokenCustomizer 回调实例
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            // 仅在生成 access_token 时注入业务字段
            if ("access_token".equals(context.getTokenType().getValue())) {
                // context.getPrincipal() 返回的是 Authentication 对象（AdminPasswordCodeAuthenticationToken）
                Authentication authentication = context.getPrincipal();

                // 提取角色权限集合并去除 ROLE_ 前缀
                // Resource Server 的 JwtAuthenticationConverter 会统一添加 ROLE_ 前缀
                // 若此处保留前缀，会导致解析后变成 ROLE_ROLE_GUEST，所有 hasRole 校验失败
                Set<String> roles = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                        .collect(Collectors.toSet());
                // 写入 roles claim，供 Resource Server 的 JwtAuthenticationConverter 解析为 GrantedAuthority
                context.getClaims().claim("roles", roles);

                // 提取业务用户信息（userId/username/email/avatar），写入 JWT claims 供业务代码直接读取
                // authentication.getPrincipal() 才是真正的用户对象（SecurityUser）
                Object principal = authentication.getPrincipal();
                if (principal instanceof SecurityUser securityUser) {
                    // 用户 ID：业务代码通过 jwt.getClaim("user_id") 获取，避免查库
                    context.getClaims().claim("user_id", securityUser.getUserId());
                    // 用户名：前端展示用，避免额外请求用户信息接口
                    if (securityUser.getUsername() != null) {
                        context.getClaims().claim("username", securityUser.getUsername());
                    }
                    // 邮箱：/me 接口与前端展示用
                    if (securityUser.getEmail() != null) {
                        context.getClaims().claim("email", securityUser.getEmail());
                    }
                    // 头像 URL：/me 接口与前端展示用
                    // 注:新建用户可能尚未设置头像,需判空,否则 JwtClaimsSet.Builder.claim 会抛 IllegalArgumentException
                    if (securityUser.getAvatar() != null) {
                        context.getClaims().claim("avatar", securityUser.getAvatar());
                    }
                }
            }
        };
    }
}
