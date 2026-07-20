package com.xuan.auth.config;

import com.xuan.auth.security.CustomOAuth2UserService;
import com.xuan.auth.security.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 第三方 OAuth2 登录配置
 * <p>
 * 阶段四新增：为 GitHub/Gitee 登录配置独立的 SecurityFilterChain。
 * </p>
 *
 * <h3>为什么需要独立 FilterChain？</h3>
 * <p>
 * 项目整体架构是 STATELESS + JWT（{@link com.xuan.resource.config.ResourceServerConfig}），
 * 但 Spring Security 的 {@code oauth2Login()} 默认依赖 Session 来暂存 OAuth2 协商过程中的
 * 认证状态（authorization request、state 参数等）。若强制 STATELESS，OAuth2 回调将失败。
 * </p>
 *
 * <p>解决方案：使用独立的 SecurityFilterChain（{@code @Order(3)}），仅在以下路径生效：</p>
 * <ul>
 *     <li>{@code /oauth2/authorization/github} / {@code /oauth2/authorization/gitee}：发起 OAuth2 登录</li>
 *     <li>{@code /login/oauth2/code/github} / {@code /login/oauth2/code/gitee}：OAuth2 回调</li>
 *     <li>{@code /login/oauth2/code/*}：通用 OAuth2 回调路径</li>
 * </ul>
 *
 * <p>该链使用 {@code IF_REQUIRED} session 策略：仅 OAuth2 流程需要时才创建 Session，
 * 其他请求仍走 ResourceServerConfig（STATELESS）。</p>
 *
 * <h3>FilterChain 优先级</h3>
 * <table border="1">
 *     <tr><th>Order</th><th>FilterChain</th><th>处理路径</th></tr>
 *     <tr><td>1</td><td>AuthorizationServerConfig</td><td>/oauth2/token、/oauth2/jwks 等授权服务器端点</td></tr>
 *     <tr><td>2</td><td>ResourceServerConfig</td><td>/admin/**、/blog/** 等业务 API</td></tr>
 *     <tr><td>3</td><td>OAuth2LoginConfig（本类）</td><td>/oauth2/authorization/**、/login/oauth2/code/**</td></tr>
 * </table>
 *
 * <p>注意：{@code @Order(3)} 低于 {@code @Order(2)}，但 securityMatcher 限制了仅 OAuth2 登录路径才会命中本链，
 * 不会与 ResourceServerConfig 冲突。</p>
 *
 * <h3>登录流程</h3>
 * <pre>
 * 1. 前端跳转到 GET /oauth2/authorization/github
 *    └─ Spring Security 重定向到 GitHub 授权页面
 *
 * 2. 用户在 GitHub 授权后回调到 GET /login/oauth2/code/github?code=xxx&state=xxx
 *    └─ Spring Security 通过 AuthorizationCodeTokenClient 换取 Access Token
 *    └─ 调用 CustomOAuth2UserService.loadUser() 拉取用户信息并查找/创建本地 sys_user
 *    └─ 触发 OAuth2LoginSuccessHandler
 *       ├─ 使用 JwtEncoder 生成 JWT（与 SAS 颁发格式一致）
 *       ├─ 写入 access_token Cookie（HttpOnly）
 *       ├─ 清除 SecurityContext（防止 Session 持久化 OAuth2 认证）
 *       └─ 重定向到前端首页
 *
 * 3. 后续请求由 ResourceServerConfig 处理
 *    └─ CookieBearerTokenResolver 从 Cookie 读取 access_token
 *    └─ JwtAuthenticationConverter 解析 roles claim 构造 Authentication
 *    └─ 业务接口正常处理
 * </pre>
 *
 * @author xuan
 */
@Configuration
@EnableWebSecurity
public class OAuth2LoginConfig {

    /**
     * OAuth2 登录安全过滤器链
     * <p>
     * 仅处理 OAuth2 登录相关路径，其他请求由 ResourceServerConfig 处理。
     * </p>
     */
    @Bean
    @Order(3)
    public SecurityFilterChain oauth2LoginSecurityFilterChain(HttpSecurity http,
                                                               CustomOAuth2UserService customOAuth2UserService,
                                                               JwtEncoder jwtEncoder) throws Exception {
        http
                // 仅匹配 OAuth2 登录路径
                .securityMatcher(
                        "/oauth2/authorization/**",   // 发起 OAuth2 登录
                        "/login/oauth2/code/**"       // OAuth2 回调
                )
                .csrf(csrf -> csrf.disable())   // OAuth2 流程禁用 CSRF（POST 回调由 Spring Security 处理）
                // IF_REQUIRED：仅 OAuth2 流程需要时才创建 Session，回调完成后 Session 可清理
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(new OAuth2LoginSuccessHandler(jwtEncoder))
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
