package com.xuan.resource.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuan.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 Resource Server 配置类
 * <p>
 * 阶段三 RBAC 权限模型的第一层防线：URL 粗粒度权限控制。
 * </p>
 *
 * <h3>核心职责</h3>
 * <ol>
 *     <li>JWT Token 校验（基于 JWKS 离线验签）</li>
 *     <li>JWT roles claim → Spring Security GrantedAuthority 映射（自动加 ROLE_ 前缀）</li>
 *     <li>按 HTTP 方法 + URL 模式做粗粒度角色过滤（精细化交给类级/方法级 @PreAuthorize）</li>
 *     <li>注册自定义 BearerTokenResolver，支持 Header + Cookie 双轨 Token 解析</li>
 *     <li>统一 401/403 JSON 响应格式（替代旧拦截器抛异常的方式）</li>
 * </ol>
 *
 * <h3>三层权限架构</h3>
 * <ul>
 *     <li>第一层（本类）：URL 粗粒度，按 HTTP 方法快速拒绝</li>
 *     <li>第二层：Controller 类级 @PreAuthorize（模块隔离 AUTHOR）</li>
 *     <li>第三层：Controller 方法级 @PreAuthorize（含 SpEL 数据范围校验）</li>
 * </ul>
 *
 * <h3>URL 权限规则（按匹配顺序）</h3>
 * <pre>
 * /oauth2/**                            → permitAll（SAS 自身端点，由 @Order(1) 链处理）
 * /admin/server-monitor/**              → hasRole('ADMIN')（敏感信息仅 ADMIN）
 * GET    /admin/**                      → hasAnyRole('ADMIN','AUTHOR','AUDITOR')（后台读权限）
 * POST   /admin/**                      → hasAnyRole('ADMIN','AUTHOR')（后台写权限，AUDITOR 排除）
 * PUT    /admin/**                      → hasAnyRole('ADMIN','AUTHOR')
 * DELETE /admin/**                      → hasAnyRole('ADMIN','AUTHOR')
 * other                                  → permitAll（blog/cv/home 在阶段四处理）
 * </pre>
 *
 * @author xuan
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {

    /**
     * 全局 JSON 序列化器，用于 401/403 响应体序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * Resource Server 安全过滤器链
     * <p>
     * 通过 @Order(2) 优先级低于 @Order(1) 的授权服务器链，
     * 确保请求先匹配授权服务器端点（/oauth2/**），未命中再走 Resource Server 链。
     * </p>
     *
     * <h3>关键配置说明</h3>
     * <ul>
     *     <li>csrf.disable：无状态 API 不需要 CSRF Token</li>
     *     <li>sessionCreationPolicy(STATELESS)：不创建 HttpSession，完全依赖 JWT</li>
     *     <li>bearerTokenResolver：自定义双轨解析器（Header 优先，Cookie 兜底）</li>
     *     <li>authenticationEntryPoint：401 JSON 响应处理器</li>
     *     <li>accessDeniedHandler：403 JSON 响应处理器</li>
     * </ul>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // 注册双轨 Token 解析器：Header 优先，Cookie 兜底
                        .bearerTokenResolver(cookieBearerTokenResolver())
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new CustomAccessDeniedHandler(objectMapper))
                )
                .authorizeHttpRequests(auth -> auth
                        // 公开端点（无需认证）
                        // /oauth2/** 由 @Order(1) 授权服务器链优先处理，这里 permitAll 仅作兜底
                        .requestMatchers("/oauth2/**").permitAll()

                        // 敏感路径：仅 ADMIN 可访问
                        // 服务器监控包含 CPU/内存/磁盘等敏感信息，仅 ADMIN
                        .requestMatchers("/admin/server-monitor/**").hasRole("ADMIN")

                        // 后台 GET 请求：ADMIN + AUTHOR + AUDITOR 可访问（GUEST 排除）
                        // 注：AUTHOR 仅能访问文章相关 GET，类级 @PreAuthorize 会进一步隔离
                        .requestMatchers(HttpMethod.GET, "/admin/**").hasAnyRole("ADMIN", "AUTHOR", "AUDITOR")

                        // 后台非 GET 请求：ADMIN + AUTHOR（AUDITOR/GUEST 排除）
                        // AUTHOR 在方法级再细控（仅能操作自己的文章），其他写操作由类级 @PreAuthorize 排除
                        .requestMatchers(HttpMethod.POST, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
                        .requestMatchers(HttpMethod.PUT, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
                        .requestMatchers(HttpMethod.DELETE, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")

                        // 其他路径：暂时放行（blog/cv/home 在阶段四处理）
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /**
     * JWT 权限转换器
     * <p>
     * 从 JWT 的 roles claim 提取权限，并添加 ROLE_ 前缀，与 Spring Security 的
     * hasRole() / hasAnyRole() 表达式约定一致（hasRole('ADMIN') 实际匹配 ROLE_ADMIN）。
     * </p>
     *
     * <h3>JWT 结构示例</h3>
     * <pre>
     * {
     *   "sub": "admin",
     *   "roles": ["ROLE_ADMIN", "ROLE_AUTHOR", "ROLE_GUEST"],
     *   "scope": ["openid", "profile", "admin", "author", "auditor"],
     *   "exp": 1737000000
     * }
     * </pre>
     *
     * <h3>转换流程</h3>
     * <ol>
     *     <li>JwtGrantedAuthoritiesConverter 读取 roles claim</li>
     *     <li>每个 role 添加 ROLE_ 前缀（如 ADMIN → ROLE_ADMIN）</li>
     *     <li>设置 principal 为 sub claim（用户名）</li>
     * </ol>
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Spring Security 约定：角色权限以 ROLE_ 前缀存储，hasRole('ADMIN') 实际匹配 ROLE_ADMIN
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        // JWT 中存储角色的 claim 名称（由 JwtCustomizerConfig 写入）
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        // principal 设为 sub claim（用户名），后续可通过 authentication.getName() 获取
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    /**
     * 自定义 Bearer Token 解析器 Bean
     * <p>
     * 注入到 OAuth2ResourceServer，使其在解析 Token 时优先读 Authorization Header，
     * 若 Header 中无 Token 再读 access_token Cookie。
     * </p>
     *
     * @return CookieBearerTokenResolver 实例
     */
    @Bean
    public BearerTokenResolver cookieBearerTokenResolver() {
        return new CookieBearerTokenResolver();
    }

    /**
     * 未认证异常处理：返回 401 JSON
     * <p>
     * 触发场景：
     * <ul>
     *     <li>请求未携带 Token（Header 和 Cookie 都没有）</li>
     *     <li>Token 已过期</li>
     *     <li>Token 签名验证失败</li>
     * </ul>
     * </p>
     */
    public static class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException, ServletException {
            log.warn("未认证访问: {}, 异常: {}", request.getRequestURI(), authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), Result.error("未认证，请先登录"));
        }
    }

    /**
     * 权限不足异常处理：返回 403 JSON
     * <p>
     * 触发场景：
     * <ul>
     *     <li>角色不匹配（如 GUEST 访问 /admin/**）</li>
     *     <li>@PreAuthorize 表达式求值为 false（如 AUTHOR 编辑他人文章）</li>
     * </ul>
     * </p>
     */
    public static class CustomAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException, ServletException {
            log.warn("权限不足: {}, 异常: {}", request.getRequestURI(), accessDeniedException.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), Result.error("权限不足"));
        }
    }
}
