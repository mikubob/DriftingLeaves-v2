package com.xuan.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuan.entity.SysUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 第三方 OAuth2 登录成功处理器
 * <p>
 * GitHub/Gitee 登录成功后,将 OAuth2 身份转换为本项目标准的 JWT Token,
 * 写入 HttpOnly Cookie,并重定向到前端首页。
 * </p>
 *
 * <h3>为什么需要这个 Handler?</h3>
 * <p>
 * Spring Security 的 {@code oauth2Login()} 默认使用 Session 持久化认证状态,
 * 而本项目是 STATELESS + JWT 架构。因此 OAuth2 登录成功后必须:
 * </p>
 * <ol>
 *     <li>从 OAuth2User 中取出本地 sys_user 信息(由 CustomOAuth2UserService 写入)</li>
 *     <li>构造 principal 为 SecurityUser 的 Authentication(供 JwtCustomizerConfig 识别)</li>
 *     <li>用 OAuth2TokenGenerator 生成 access_token JWT + refresh_token(复用 SAS 标准流程)</li>
 *     <li>通过 OAuth2AuthorizationService 持久化授权记录(使 refresh_token grant 可用)</li>
 *     <li>写入 access_token + refresh_token Cookie(与 OAuth2TokenResponseCookieHandler 格式一致)</li>
 *     <li>重定向到前端首页(前端从 Cookie 读取 Token 完成后续请求)</li>
 * </ol>
 *
 * <h3>P1-2 改造:补齐 refresh_token</h3>
 * <p>
 * 原实现仅用 JwtEncoder 手动生成 access_token,无 refresh_token,且未持久化授权记录,
 * 导致 OAuth2 登录用户无法使用 refresh_token grant 刷新 Token。P1-2 改造为:
 * </p>
 * <ul>
 *     <li>改用 {@link OAuth2TokenGenerator} 生成 access_token + refresh_token</li>
 *     <li>access_token 经 JwtGenerator 生成,会自动调用 {@link com.xuan.auth.config.JwtCustomizerConfig}
 *         注入 roles/user_id/nickname/email/avatar claims(与其他 grant_type 保持一致)</li>
 *     <li>refresh_token 经 OAuth2RefreshTokenGenerator 生成(不透明随机字符串,7 天有效)</li>
 *     <li>通过 {@link OAuth2AuthorizationService#save} 持久化授权记录到 oauth2_authorization 表</li>
 *     <li>Authorization 中存储 Principal attribute(供 refresh 时 JwtCustomizerConfig 重新注入 claims)</li>
 * </ul>
 *
 * <h3>使用 blog-client 作为持久化客户端</h3>
 * <p>
 * OAuth2 第三方登录用户都是博客端用户(GUEST 角色),使用数据库中预置的 {@code blog-client}
 * (支持 authorization_code, refresh_token, email_code)持久化授权记录。
 * blog-client 配置了 refresh_token grant_type,因此 refresh_token 流程可正常工作。
 * </p>
 *
 * <h3>自定义 grant_type: oauth2_login</h3>
 * <p>
 * 使用 {@code new AuthorizationGrantType("oauth2_login")} 标记此类授权记录来源于第三方登录,
 * 便于区分和审计。SAS 的 OAuth2RefreshTokenAuthenticationProvider 不会校验具体 grant_type 值,
 * 只要 RegisteredClient 支持 refresh_token 即可。
 * </p>
 *
 * <h3>多前端源回跳(决策 4,P1-3 实现)</h3>
 * <p>
 * 支持 Admin/Blog 端跳不同地址:前端发起 OAuth2 登录时在 state 参数中编码 redirect_uri,
 * 本处理器解码 state 取 redirect_uri,经白名单校验后跳转。state 缺失时 fallback 到
 * {@code dl.oauth2-redirect.success-url}。详见 P1-3 改造。
 * </p>
 *
 * @author xuan
 * @see CustomOAuth2UserService 提供 LOCAL_USER_ATTR_KEY / LOCAL_AUTHORITIES_ATTR_KEY
 * @see CookieUtils Cookie 写入工具类
 * @see CookieConstant Cookie 名称与属性常量
 * @see EmailCodeAuthenticationProvider 参考其 token 生成与持久化模式
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /**
     * blog-client 的 client_id(用于持久化 OAuth2Authorization 记录)
     * <p>
     * OAuth2 第三方登录用户都是博客端用户,使用 blog-client(支持 refresh_token grant)。
     * </p>
     */
    private static final String BLOG_CLIENT_ID = "blog-client";

    /**
     * 自定义授权类型:第三方 OAuth2 登录
     * <p>
     * 标记此类授权记录来源于 GitHub/Gitee 登录,便于审计。SAS 不会校验具体值。
     * </p>
     */
    private static final AuthorizationGrantType OAUTH2_LOGIN_GRANT_TYPE =
            new AuthorizationGrantType("oauth2_login");

    /**
     * JWT Token 生成器(SAS 提供,内部包含 JwtGenerator + OAuth2RefreshTokenGenerator)
     * <p>
     * 复用 SAS 标准 token 生成流程,确保 access_token 经 JwtCustomizerConfig 注入业务 claims。
     * </p>
     */
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    /**
     * 授权记录服务(用于持久化 OAuth2Authorization,使 refresh_token grant 可用)
     */
    private final OAuth2AuthorizationService authorizationService;

    /**
     * 客户端注册信息仓库(用于查找 blog-client)
     */
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 登录成功后重定向的前端 URL(state 参数缺失或校验失败时的 fallback)
     */
    @Value("${dl.oauth2-redirect.success-url:http://localhost:5173/}")
    private String successRedirectUrl;

    /**
     * 允许回跳的前端源白名单(逗号分隔,格式:scheme://host[:port])
     * <p>
     * P1-3 多源回跳安全校验:state 中编码的 redirect_uri 必须在白名单内才会跳转,
     * 防止开放重定向漏洞。
     * </p>
     * <p>
     * 默认值覆盖本地开发环境(Admin 5173 / Blog 5174),生产环境通过 application-prod.yml 覆盖。
     * </p>
     */
    @Value("${dl.oauth2-redirect.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private List<String> allowedOrigins;

    /**
     * JSON 解析器(用于解析 state 中编码的 JSON)
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 登录认证成功,开始生成 JWT Token: name={}", authentication.getName());

        // 1. 从 OAuth2User 中取出本地 sys_user(由 CustomOAuth2UserService 写入 attributes)
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        SysUser localUser = (SysUser) oAuth2User.getAttributes()
                .get(CustomOAuth2UserService.LOCAL_USER_ATTR_KEY);

        if (localUser == null) {
            log.error("OAuth2 登录成功但未找到本地 sys_user, attributes={}",
                    oAuth2User.getAttributes());
            response.sendRedirect(successRedirectUrl + "?error=oauth_user_not_found");
            return;
        }

        // 2. 查找 blog-client(用于持久化授权记录)
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(BLOG_CLIENT_ID);
        if (registeredClient == null) {
            log.error("OAuth2 登录持久化失败: blog-client 未注册,无法保存授权记录");
            response.sendRedirect(successRedirectUrl + "?error=server_error");
            return;
        }

        // 3. 构造 principal 为 SecurityUser 的 Authentication
        //    JwtCustomizerConfig 会从 authentication.getPrincipal() 取 SecurityUser,注入 user_id/email 等 claims
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        SecurityUser securityUser = new SecurityUser(
                localUser.getId(),
                localUser.getUsername(),
                localUser.getPassword(),
                localUser.getUserType(),
                localUser.getNickname(),
                localUser.getEmail(),
                localUser.getAvatar(),
                authorities
        );
        UsernamePasswordAuthenticationToken principalAuth =
                new UsernamePasswordAuthenticationToken(securityUser, null, authorities);

        // 4. 构造 OAuth2TokenContext(供 OAuth2TokenGenerator 使用)
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principalAuth)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(Collections.emptySet())
                .authorizationGrantType(OAUTH2_LOGIN_GRANT_TYPE)
                .authorizationGrant(principalAuth);

        // 5. 生成 access_token(JWT,经 JwtCustomizerConfig 注入业务 claims)
        OAuth2TokenContext accessTokenContext = tokenContextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();
        OAuth2Token generatedAccessToken = tokenGenerator.generate(accessTokenContext);
        if (generatedAccessToken == null) {
            log.error("OAuth2 登录生成 access_token 失败: userId={}", localUser.getId());
            response.sendRedirect(successRedirectUrl + "?error=server_error");
            return;
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(),
                generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(),
                Collections.emptySet()
        );

        // 6. 生成 refresh_token(不透明随机字符串,7 天有效)
        //    仅当 blog-client 配置了 refresh_token grant_type 时才生成
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(
                AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2TokenContext refreshTokenContext = tokenContextBuilder
                    .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                    .build();
            OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshTokenContext);
            if (generatedRefreshToken instanceof OAuth2RefreshToken rt) {
                refreshToken = rt;
            } else {
                log.warn("OAuth2 登录生成 refresh_token 失败(返回 null 或类型不匹配): userId={}",
                        localUser.getId());
            }
        }

        // 7. 构造并持久化 OAuth2Authorization 记录
        //    存储 Principal attribute,供 refresh_token grant 时 JwtCustomizerConfig 重新注入 claims
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(localUser.getUsername())
                .authorizationGrantType(OAUTH2_LOGIN_GRANT_TYPE)
                .authorizedScopes(Collections.emptySet())
                .attribute(Principal.class.getName(), principalAuth)
                .token(accessToken, metadata -> {
                    if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
                        metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims());
                    }
                    metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, false);
                });

        if (refreshToken != null) {
            authorizationBuilder.refreshToken(refreshToken);
        }

        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);

        // 8. 写入 HttpOnly Cookie(access_token + refresh_token)
        CookieUtils.addAccessTokenCookie(response, request, accessToken.getTokenValue());
        if (refreshToken != null) {
            CookieUtils.addRefreshTokenCookie(response, request, refreshToken.getTokenValue());
        }

        log.info("OAuth2 登录成功: userId={}, username={}, refreshToken={}",
                localUser.getId(), localUser.getUsername(), refreshToken != null);

        // 9. 清除 SecurityContext(避免 Session 持有 OAuth2 认证信息,与 STATELESS 架构一致)
        //    注:由于 OAuth2LoginConfig 使用 IF_REQUIRED session 策略,session 仅临时存在
        //    重定向前清理认证状态,确保后续请求完全依赖 JWT Cookie
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        // 10. 重定向到前端(P1-3 多源回跳:解码 state 取 redirect_uri,校验白名单后跳转)
        String redirectUrl = resolveRedirectUrl(request);
        response.sendRedirect(redirectUrl);
    }

    /**
     * 解析 state 参数中的 redirect_uri,校验白名单后返回最终跳转地址
     * <p>
     * P1-3 多源回跳实现(决策 4,用户偏好:用 state 编码而非 redirect_uri query 参数)。
     * </p>
     *
     * <h3>解析流程</h3>
     * <ol>
     *     <li>从 request 取 state 参数</li>
     *     <li>state 为空 → 返回 successRedirectUrl(fallback)</li>
     *     <li>base64 URL 解码 state</li>
     *     <li>解析 JSON 取 redirect_uri 字段</li>
     *     <li>校验 redirect_uri 的 scheme://host[:port] 是否在白名单内</li>
     *     <li>校验通过 → 返回 redirect_uri;校验失败 → 返回 successRedirectUrl</li>
     * </ol>
     *
     * <h3>容错策略</h3>
     * <p>
     * state 不是 base64 编码的 JSON(如 Spring Security 自行生成的随机 state)时,
     * 解码或解析会抛异常,此时静默 fallback 到 successRedirectUrl。
     * </p>
     *
     * @param request HTTP 请求
     * @return 最终跳转地址
     */
    private String resolveRedirectUrl(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (!StringUtils.hasText(state)) {
            return successRedirectUrl;
        }

        try {
            // 1. base64 URL 解码(state 可能由前端用 Base64.getUrlEncoder() 编码)
            byte[] decoded = Base64.getUrlDecoder().decode(state);
            String json = new String(decoded, StandardCharsets.UTF_8);

            // 2. 解析 JSON 取 redirect_uri
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(json, Map.class);
            Object redirectUriObj = map.get("redirect_uri");
            if (!(redirectUriObj instanceof String redirectUri) || !StringUtils.hasText(redirectUri)) {
                log.debug("OAuth2 state 中未包含 redirect_uri,使用 fallback: state={}", state);
                return successRedirectUrl;
            }

            // 3. 校验白名单(只比较 scheme://host[:port],忽略 path/query/fragment)
            if (!isAllowedOrigin(redirectUri)) {
                log.warn("OAuth2 state 中的 redirect_uri 不在白名单内,已拦截并 fallback: {}",
                        redirectUri);
                return successRedirectUrl;
            }

            log.info("OAuth2 多源回跳: state 解码成功, redirect_uri={}", redirectUri);
            return redirectUri;
        } catch (Exception e) {
            // state 不是前端编码的 base64 JSON(可能是 Spring Security 自行生成的随机 state)
            log.debug("OAuth2 state 解析失败,使用 fallback: state={}, error={}",
                    state, e.getMessage());
            return successRedirectUrl;
        }
    }

    /**
     * 校验 URL 的源(scheme://host[:port])是否在白名单内
     * <p>
     * 仅比较 origin 部分,忽略 path/query/fragment。例如:
     * </p>
     * <pre>
     * 白名单: http://localhost:5174
     * 输入:   http://localhost:5174/#/login  → 通过
     * 输入:   http://localhost:5174/login    → 通过
     * 输入:   http://evil.com/#/login        → 拒绝
     * </pre>
     *
     * @param url 待校验的完整 URL
     * @return true 表示 origin 在白名单内
     */
    private boolean isAllowedOrigin(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                return false;
            }
            String origin = port == -1
                    ? scheme + "://" + host
                    : scheme + "://" + host + ":" + port;
            return allowedOrigins.contains(origin);
        } catch (Exception e) {
            return false;
        }
    }
}
