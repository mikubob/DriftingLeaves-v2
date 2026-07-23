package com.xuan.auth.config;

import com.xuan.auth.security.CustomOAuth2UserService;
import com.xuan.auth.security.OAuth2LoginSuccessHandler;
import com.xuan.auth.security.OAuth2StateAuthorizationRequestResolver;
import com.xuan.properties.OAuth2LoginProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import com.xuan.auth.security.OAuth2LoginSuccessHandler;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * 第三方 OAuth2 登录配置
 * <p>
 * 为 GitHub/Gitee 登录配置独立的 SecurityFilterChain。
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
 * <h3>ClientRegistration 注册策略</h3>
 * <p>
 * 不使用 {@code spring.security.oauth2.client} 自动配置（其要求 client-id 必须非空，开发期未配置会启动失败），
 * 改为读取 {@link OAuth2LoginProperties}（{@code dl.oauth2.*}）手动构建 ClientRegistrationRepository。
 * </p>
 * <ul>
 *     <li>{@code dl.oauth2.github.client-id} 非空 → 注册 GitHub</li>
 *     <li>{@code dl.oauth2.gitee.client-id} 非空 → 注册 Gitee</li>
 *     <li>两者都为空 → 不注册 ClientRegistrationRepository Bean，OAuth2LoginSecurityFilterChain 也不创建</li>
 * </ul>
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
     * GitHub OAuth2 提供商通用配置
     * <p>
     * GitHub 的 OAuth2 端点固定，参考：
     * https://docs.github.com/zh/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps
     * </p>
     */
    private static final String GITHUB_AUTHORIZATION_URI = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URI = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_INFO_URI = "https://api.github.com/user";
    private static final String GITHUB_USER_NAME_ATTRIBUTE = "id";

    /**
     * Gitee OAuth2 提供商通用配置
     * <p>
     * Gitee 的 OAuth2 端点固定，参考：
     * https://gitee.com/api/v5/oauth_doc
     * </p>
     */
    private static final String GITEE_AUTHORIZATION_URI = "https://gitee.com/oauth/authorize";
    private static final String GITEE_TOKEN_URI = "https://gitee.com/oauth/token";
    private static final String GITEE_USER_INFO_URI = "https://gitee.com/api/v5/user";
    private static final String GITEE_USER_NAME_ATTRIBUTE = "id";

    /**
     * ClientRegistrationRepository Bean
     * <p>
     * 仅在至少有一个第三方平台（GitHub/Gitee）配置了完整的 client-id 和 client-secret 时才创建。
     * 都未配置时不创建 Bean（通过自定义 {@link OAuth2ClientConfiguredCondition} 控制），
     * {@link #oauth2LoginSecurityFilterChain} 也不会注册。
     * </p>
     * <p>
     * 不能用 {@code @ConditionalOnBean(OAuth2LoginProperties.class)}，
     * 因为 OAuth2LoginProperties 是 {@code @Component} 永远会注册，条件永远成立。
     * 也不能用 {@code @ConditionalOnProperty}，因为空字符串仍视为"存在"。
     * 改用自定义 Condition 调用 {@link OAuth2LoginProperties#hasAnyConfigured()} 严格判断。
     * </p>
     */
    @Bean
    @org.springframework.context.annotation.Conditional(OAuth2LoginConfig.OAuth2ClientConfiguredCondition.class)
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2LoginProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();

        // GitHub
        if (properties.isGithubConfigured()) {
            registrations.add(buildGithubRegistration(
                    properties.getGithub().getClientId(),
                    properties.getGithub().getClientSecret()));
        }

        // Gitee
        if (properties.isGiteeConfigured()) {
            registrations.add(buildGiteeRegistration(
                    properties.getGitee().getClientId(),
                    properties.getGitee().getClientSecret()));
        }

        // 此处 registrations 一定非空（Condition 已保证），无需再判空
        return new InMemoryClientRegistrationRepository(registrations);
    }

    /**
     * 自定义条件：仅当 {@link OAuth2LoginProperties#hasAnyConfigured()} 返回 true 时才匹配
     * <p>
     * 用于精确控制 {@link #clientRegistrationRepository} 和 {@link #oauth2LoginSecurityFilterChain}
     * 两个 Bean 的创建：未配置任何第三方平台时不创建这两个 Bean，应用正常启动。
     * </p>
     * <p>
     * 实现说明：通过 {@link org.springframework.core.env.Environment} 直接读取配置项，
     * 避免在 Condition 阶段提前实例化 OAuth2LoginProperties Bean（防止 Bean 创建顺序问题）。
     * </p>
     */
    public static class OAuth2ClientConfiguredCondition implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                               org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            org.springframework.core.env.Environment env = context.getEnvironment();
            String githubClientId = env.getProperty("dl.oauth2.github.client-id", "");
            String githubClientSecret = env.getProperty("dl.oauth2.github.client-secret", "");
            String giteeClientId = env.getProperty("dl.oauth2.gitee.client-id", "");
            String giteeClientSecret = env.getProperty("dl.oauth2.gitee.client-secret", "");

            boolean githubConfigured = isNonEmpty(githubClientId) && isNonEmpty(githubClientSecret);
            boolean giteeConfigured = isNonEmpty(giteeClientId) && isNonEmpty(giteeClientSecret);

            return githubConfigured || giteeConfigured;
        }

        private static boolean isNonEmpty(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    /**
     * 构建 GitHub ClientRegistration
     */
    private ClientRegistration buildGithubRegistration(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("github")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri(GITHUB_AUTHORIZATION_URI)
                .tokenUri(GITHUB_TOKEN_URI)
                .userInfoUri(GITHUB_USER_INFO_URI)
                .userNameAttributeName(GITHUB_USER_NAME_ATTRIBUTE)
                .clientName("GitHub")
                .build();
    }

    /**
     * 构建 Gitee ClientRegistration
     * <p>
     * 注意：Gitee 不支持 client_secret_basic（Basic Auth），需用 client_secret_post
     * </p>
     * <p>
     * Gitee 支持的 scope 参考：https://gitee.com/api/v5/oauth_doc
     * 仅申请 user_info 即可获取用户公开信息（含 email）。
     * </p>
     */
    private ClientRegistration buildGiteeRegistration(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("gitee")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("user_info")
                .authorizationUri(GITEE_AUTHORIZATION_URI)
                .tokenUri(GITEE_TOKEN_URI)
                .userInfoUri(GITEE_USER_INFO_URI)
                .userNameAttributeName(GITEE_USER_NAME_ATTRIBUTE)
                .clientName("Gitee")
                .build();
    }

    /**
     * OAuth2 登录安全过滤器链
     * <p>
     * 仅当 ClientRegistrationRepository Bean 存在时才创建（即至少配置了一个第三方平台）。
     * </p>
     */
    @Bean
    @org.springframework.context.annotation.Conditional(OAuth2LoginConfig.OAuth2ClientConfiguredCondition.class)
    @Order(3)
    public SecurityFilterChain oauth2LoginSecurityFilterChain(HttpSecurity http,
                                                               ClientRegistrationRepository clientRegistrationRepository,
                                                               CustomOAuth2UserService customOAuth2UserService,
                                                               OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) throws Exception {
        http
                // 仅匹配 OAuth2 登录路径
                .securityMatcher(
                        "/oauth2/authorization/**",   // 发起 OAuth2 登录
                        "/login/oauth2/code/**"       // OAuth2 回调
                )
                .csrf(csrf -> csrf.disable())   // OAuth2 流程禁用 CSRF（POST 回调由 Spring Security 处理）
                // IF_REQUIRED：仅 OAuth2 流程需要时才创建 Session，回调完成后 Session 可清理
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // ClientRegistrationRepository 由 Spring Security OAuth2 Client 自动配置拾取
                // （InMemoryClientRegistrationRepository Bean 被自动注入到 OAuth2LoginAuthenticationFilter）
                .oauth2Login(oauth2 -> oauth2
                        // P1-3 多源回跳:自定义 resolver 保留前端传的 state 参数(编码 redirect_uri)
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(
                                        new OAuth2StateAuthorizationRequestResolver(clientRegistrationRepository))
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        // P1-2 改造:使用 Spring 容器管理的 OAuth2LoginSuccessHandler,
                        // 使其 @Value / @PostConstruct 等注解生效
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
