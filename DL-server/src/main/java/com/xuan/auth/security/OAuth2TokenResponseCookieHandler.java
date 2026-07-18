package com.xuan.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 Token 响应 Cookie 下发处理器
 * <p>
 * 阶段三步骤 6 的核心组件：在 Spring Authorization Server 颁发 Token 成功后，
 * 将 access_token / refresh_token 以 HttpOnly Cookie 形式写入响应，
 * 同时保留默认的 JSON 响应体输出，实现 Cookie + Header 双轨兼容。
 * </p>
 *
 * <h3>设计动机</h3>
 * <ul>
 *     <li>Cookie 模式：浏览器自动携带 Token，前端无需手动管理 Authorization Header，简化开发</li>
 *     <li>Header 模式：兼容旧前端、移动端、跨域 API 调用等场景</li>
 *     <li>双轨并行：响应同时包含 Set-Cookie 头和 JSON body，前端可任选一种模式使用</li>
 * </ul>
 *
 * <h3>触发时机</h3>
 * <p>
 * SAS Token Endpoint（{@code POST /oauth2/token}）认证成功后，Spring Security 调用本处理器的
 * {@link #onAuthenticationSuccess} 方法。包括以下 grant_type 场景：
 * </p>
 * <ul>
 *     <li>{@code admin_password_code}：自定义管理员登录（用户名+密码+验证码）</li>
 *     <li>{@code refresh_token}：刷新 Token</li>
 *     <li>{@code authorization_code}：标准 OAuth2 授权码流程（阶段四使用）</li>
 *     <li>{@code client_credentials}：客户端凭证流程</li>
 * </ul>
 *
 * <h3>Cookie 安全属性</h3>
 * <table border="1">
 *     <tr><th>属性</th><th>值</th><th>说明</th></tr>
 *     <tr><td>HttpOnly</td><td>true</td><td>禁止 JavaScript 访问，防止 XSS 窃取 Token</td></tr>
 *     <tr><td>Path</td><td>/</td><td>整站有效</td></tr>
 *     <tr><td>Max-Age</td><td>与 Token 过期时间对齐</td><td>access_token=30min，refresh_token=7d</td></tr>
 *     <tr><td>SameSite</td><td>Strict</td><td>禁止跨站发送，防止 CSRF 攻击</td></tr>
 *     <tr><td>Secure</td><td>动态判断</td><td>HTTPS 启用，HTTP 不启用（本地开发兼容）</td></tr>
 * </table>
 *
 * <h3>与 CookieBearerTokenResolver 的配合</h3>
 * <p>
 * 本处理器负责"写 Cookie"（SAS 端），{@link com.xuan.resource.config.CookieBearerTokenResolver}
 * 负责"读 Cookie"（Resource Server 端）。两端通过常量 {@link #ACCESS_TOKEN_COOKIE_NAME}
 * 保持 Cookie 名称一致。
 * </p>
 *
 * <h3>处理流程</h3>
 * <pre>
 * 1. SAS 认证成功 → 调用 onAuthenticationSuccess
 * 2. 判断 Authentication 类型是否为 OAuth2AccessTokenAuthenticationToken
 *    ├─ 是 → 写入 access_token Cookie + refresh_token Cookie
 *    └─ 否 → 跳过 Cookie 写入
 * 3. 委托默认处理器输出 JSON 响应体（保证双轨兼容）
 * </pre>
 *
 * <h3>使用方式</h3>
 * <p>
 * 在 {@link com.xuan.auth.config.AuthorizationServerConfig} 中通过
 * {@code .accessTokenResponseHandler(new OAuth2TokenResponseCookieHandler(
 *       new OAuth2AccessTokenResponseAuthenticationSuccessHandler()))} 注册。
 * </p>
 *
 * @author xuan
 * @see com.xuan.resource.config.CookieBearerTokenResolver Resource Server 端 Cookie 解析器
 * @see org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AccessTokenResponseAuthenticationSuccessHandler
 *       默认的 JSON 响应处理器（被本类委托调用）
 */
@Slf4j
public class OAuth2TokenResponseCookieHandler implements AuthenticationSuccessHandler {

    /**
     * access_token Cookie 名称
     * <p>
     * 与 {@link com.xuan.resource.config.CookieBearerTokenResolver#ACCESS_TOKEN_COOKIE_NAME}
     * 保持一致，确保写入和读取的 Cookie 名称相同。
     * </p>
     */
    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    /**
     * refresh_token Cookie 名称
     * <p>
     * 用于刷新 Token 时浏览器自动携带，前端无需手动管理 refresh_token。
     * </p>
     */
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    /**
     * access_token Cookie 默认存活时间：30 分钟（单位：秒）
     * <p>
     * 与 SAS 配置的 access_token 有效期保持一致。Token 过期后 Cookie 也会失效，
     * 浏览器自动清除。前端可通过 refresh_token 获取新的 access_token。
     * </p>
     */
    private static final long ACCESS_TOKEN_MAX_AGE_SECONDS = 30 * 60L;

    /**
     * refresh_token Cookie 默认存活时间：7 天（单位：秒）
     * <p>
     * 与 SAS 配置的 refresh_token 有效期保持一致。7 天内无活动则 Cookie 失效，
     * 用户需要重新登录。
     * </p>
     */
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 7 * 24 * 60 * 60L;

    /**
     * 委托的默认成功处理器
     * <p>
     * 通常为 {@code OAuth2AccessTokenResponseAuthenticationSuccessHandler}，
     * 负责将 Token 信息以标准 OAuth2 JSON 格式写入响应体（{@code {"access_token":"...", "token_type":"Bearer", ...}}）。
     * 本类在写完 Cookie 后调用它，实现 Cookie + JSON 双轨输出。
     * </p>
     * <p>
     * <b>调用顺序</b>：必须先写 Cookie，再调用委托处理器。因为委托处理器会写入响应体并触发
     * response.commit()，commit 后无法再添加 Set-Cookie 头。
     * </p>
     */
    private final AuthenticationSuccessHandler delegate;

    /**
     * 构造函数
     *
     * @param delegate 默认的成功处理器，负责将 Token 信息以 JSON 形式写入响应体
     */
    public OAuth2TokenResponseCookieHandler(AuthenticationSuccessHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * Token 颁发成功时触发
     * <p>
     * 在 SAS Token Endpoint 认证成功后被 Spring Security 调用。本方法完成两件事：
     * </p>
     * <ol>
     *     <li>如果是 OAuth2AccessTokenAuthenticationToken，提取 access_token / refresh_token 写入 Cookie</li>
     *     <li>委托给默认处理器输出标准 OAuth2 JSON 响应体（保持与原 Header 模式兼容）</li>
     * </ol>
     *
     * @param request       HTTP 请求（用于判断协议是 HTTP 还是 HTTPS，决定是否加 Secure 属性）
     * @param response      HTTP 响应（用于添加 Set-Cookie 头）
     * @param authentication 认证对象，预期为 {@link OAuth2AccessTokenAuthenticationToken}
     * @throws IOException      委托处理器写响应体时可能抛出
     * @throws ServletException 委托处理器内部 Servlet 异常
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // 仅处理 OAuth2 AccessToken 认证成功的情况（其他认证类型直接委托给默认处理器）
        if (authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth) {
            // 1. 提取并写入 access_token Cookie
            OAuth2AccessToken accessToken = tokenAuth.getAccessToken();
            if (accessToken != null) {
                addTokenCookie(response, request, ACCESS_TOKEN_COOKIE_NAME,
                        accessToken.getTokenValue(), ACCESS_TOKEN_MAX_AGE_SECONDS);
            }

            // 2. 提取并写入 refresh_token Cookie
            //    注意：client_credentials grant_type 不会颁发 refresh_token，因此需要 null 判断
            OAuth2RefreshToken refreshToken = tokenAuth.getRefreshToken();
            if (refreshToken != null) {
                addTokenCookie(response, request, REFRESH_TOKEN_COOKIE_NAME,
                        refreshToken.getTokenValue(), REFRESH_TOKEN_MAX_AGE_SECONDS);
            }

            log.debug("Token Cookie 已下发：access_token 存在={}, refresh_token 存在={}",
                    accessToken != null, refreshToken != null);
        }

        // 3. 委托给默认处理器输出 JSON 响应体（保证双轨兼容：Cookie + JSON 并存）
        //    必须在写 Cookie 之后调用，因为一旦委托处理器写响应体就会触发 response.commit()，
        //    commit 后无法再添加 Set-Cookie 头
        delegate.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * 写入 Token Cookie
     * <p>
     * 手动构造 Set-Cookie 头并添加到响应。之所以不使用 {@link jakarta.servlet.http.Cookie} +
     * {@link HttpServletResponse#addCookie}，是因为 Servlet 标准 Cookie API 不支持 SameSite 属性，
     * 必须通过 Set-Cookie 头字符串手动构造。
     * </p>
     *
     * <h3>Cookie 值编码</h3>
     * <p>
     * JWT Token 中包含特殊字符（如 {@code .} {@code _} {@code -} {@code =}），
     * 直接写入 Cookie 值可能引起解析问题。使用 {@link URLEncoder#encode} 进行 URL 编码后，
     * {@link com.xuan.resource.config.CookieBearerTokenResolver} 在读取时通过 URLDecoder.decode 解码还原。
     * </p>
     *
     * <h3>Secure 属性动态判断</h3>
     * <p>
     * 通过 {@code request.getScheme()} 判断当前请求协议：
     * </p>
     * <ul>
     *     <li>HTTPS 请求：添加 Secure 属性，浏览器仅在 HTTPS 连接下发送该 Cookie（生产环境）</li>
     *     <li>HTTP 请求：不加 Secure 属性，否则浏览器会直接丢弃该 Cookie（本地开发兼容）</li>
     * </ul>
     *
     * @param response      HTTP 响应
     * @param request       HTTP 请求（用于判断协议）
     * @param name          Cookie 名称（access_token / refresh_token）
     * @param value         Cookie 值（JWT Token 原始字符串）
     * @param maxAgeSeconds Cookie 存活时间（秒）
     */
    private void addTokenCookie(HttpServletResponse response, HttpServletRequest request,
                                String name, String value, long maxAgeSeconds) {
        // URL 编码：避免 JWT 中的特殊字符破坏 Cookie 格式
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);

        // 自动判断是否启用 Secure 属性
        boolean isSecure = "https".equalsIgnoreCase(request.getScheme());

        // 手动构造 Set-Cookie 头（Servlet Cookie API 不支持 SameSite 属性，必须手动构造）
        // Cookie 属性说明：
        // - HttpOnly：禁止 JavaScript 通过 document.cookie 访问，防止 XSS 窃取 Token
        // - Path=/：Cookie 在整站下都有效
        // - Max-Age：Cookie 存活时间（秒），与 Token 过期时间对齐
        // - SameSite=Strict：禁止跨站发送 Cookie，防止 CSRF 攻击
        // - Secure：仅在 HTTPS 连接下发送（生产环境必加，本地开发可省略）
        StringBuilder cookieBuilder = new StringBuilder()
                .append(name).append("=").append(encodedValue)
                .append("; Path=/")
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly")
                .append("; SameSite=Strict");
        if (isSecure) {
            cookieBuilder.append("; Secure");
        }

        response.addHeader("Set-Cookie", cookieBuilder.toString());
    }
}
