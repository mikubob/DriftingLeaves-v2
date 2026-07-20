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

/**
 * OAuth2 Token 响应 Cookie 下发处理器
 * <p>
 * 阶段三步骤 6 的核心组件:在 Spring Authorization Server 颁发 Token 成功后,
 * 将 access_token / refresh_token 以 HttpOnly Cookie 形式写入响应,
 * 同时保留默认的 JSON 响应体输出,实现 Cookie + Header 双轨兼容。
 * </p>
 *
 * <h3>设计动机</h3>
 * <ul>
 *     <li>Cookie 模式:浏览器自动携带 Token,前端无需手动管理 Authorization Header,简化开发</li>
 *     <li>Header 模式:兼容旧前端、移动端、跨域 API 调用等场景</li>
 *     <li>双轨并行:响应同时包含 Set-Cookie 头和 JSON body,前端可任选一种模式使用</li>
 * </ul>
 *
 * <h3>触发时机</h3>
 * <p>
 * SAS Token Endpoint({@code POST /oauth2/token})认证成功后,Spring Security 调用本处理器的
 * {@link #onAuthenticationSuccess} 方法。包括以下 grant_type 场景:
 * </p>
 * <ul>
 *     <li>{@code admin_password_code}:自定义管理员登录(用户名+密码+验证码)</li>
 *     <li>{@code refresh_token}:刷新 Token</li>
 *     <li>{@code authorization_code}:标准 OAuth2 授权码流程(阶段四使用)</li>
 *     <li>{@code client_credentials}:客户端凭证流程</li>
 * </ul>
 *
 * <h3>Cookie 安全属性</h3>
 * <p>Cookie 写入逻辑已统一到 {@link CookieUtils},具体属性策略见 {@link CookieConstant}。</p>
 *
 * <h3>与 CookieBearerTokenResolver 的配合</h3>
 * <p>
 * 本处理器负责"写 Cookie"(SAS 端),{@link com.xuan.resource.config.CookieBearerTokenResolver}
 * 负责"读 Cookie"(Resource Server 端)。两端通过 {@link CookieConstant} 保持 Cookie 名称一致。
 * </p>
 *
 * <h3>处理流程</h3>
 * <pre>
 * 1. SAS 认证成功 → 调用 onAuthenticationSuccess
 * 2. 判断 Authentication 类型是否为 OAuth2AccessTokenAuthenticationToken
 *    ├─ 是 → 写入 access_token Cookie + refresh_token Cookie
 *    └─ 否 → 跳过 Cookie 写入
 * 3. 委托默认处理器输出 JSON 响应体(保证双轨兼容)
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
 * @see CookieUtils Cookie 读写工具类
 * @see CookieConstant Cookie 名称与属性常量
 * @see com.xuan.resource.config.CookieBearerTokenResolver Resource Server 端 Cookie 解析器
 */
@Slf4j
public class OAuth2TokenResponseCookieHandler implements AuthenticationSuccessHandler {

    /**
     * 委托的默认成功处理器
     * <p>
     * 通常为 {@code OAuth2AccessTokenResponseAuthenticationSuccessHandler},
     * 负责将 Token 信息以标准 OAuth2 JSON 格式写入响应体。
     * 本类在写完 Cookie 后调用它,实现 Cookie + JSON 双轨输出。
     * </p>
     * <p>
     * <b>调用顺序</b>:必须先写 Cookie,再调用委托处理器。因为委托处理器会写入响应体并触发
     * response.commit(),commit 后无法再添加 Set-Cookie 头。
     * </p>
     */
    private final AuthenticationSuccessHandler delegate;

    /**
     * 构造函数
     *
     * @param delegate 默认的成功处理器,负责将 Token 信息以 JSON 形式写入响应体
     */
    public OAuth2TokenResponseCookieHandler(AuthenticationSuccessHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * Token 颁发成功时触发
     * <p>
     * 在 SAS Token Endpoint 认证成功后被 Spring Security 调用。本方法完成两件事:
     * </p>
     * <ol>
     *     <li>如果是 OAuth2AccessTokenAuthenticationToken,提取 access_token / refresh_token 写入 Cookie</li>
     *     <li>委托给默认处理器输出标准 OAuth2 JSON 响应体(保持与原 Header 模式兼容)</li>
     * </ol>
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // 仅处理 OAuth2 AccessToken 认证成功的情况(其他认证类型直接委托给默认处理器)
        if (authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth) {
            // 1. 提取并写入 access_token Cookie
            OAuth2AccessToken accessToken = tokenAuth.getAccessToken();
            if (accessToken != null) {
                CookieUtils.addAccessTokenCookie(response, request, accessToken.getTokenValue());
            }

            // 2. 提取并写入 refresh_token Cookie
            //    注意:client_credentials grant_type 不会颁发 refresh_token,因此需要 null 判断
            OAuth2RefreshToken refreshToken = tokenAuth.getRefreshToken();
            if (refreshToken != null) {
                CookieUtils.addRefreshTokenCookie(response, request, refreshToken.getTokenValue());
            }

            log.debug("Token Cookie 已下发:access_token 存在={}, refresh_token 存在={}",
                    accessToken != null, refreshToken != null);
        }

        // 3. 委托给默认处理器输出 JSON 响应体(保证双轨兼容:Cookie + JSON 并存)
        //    必须在写 Cookie 之后调用,因为一旦委托处理器写响应体就会触发 response.commit(),
        //    commit 后无法再添加 Set-Cookie 头
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
