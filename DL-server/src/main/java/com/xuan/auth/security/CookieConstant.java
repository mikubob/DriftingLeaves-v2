package com.xuan.auth.security;

/**
 * Token Cookie 相关常量
 * <p>
 * 统一管理 access_token / refresh_token Cookie 的名称、存活时间、属性等配置。
 * 替代原本分散在 {@link OAuth2TokenResponseCookieHandler}、{@link OAuth2LoginSuccessHandler}、
 * {@link com.xuan.resource.config.CookieBearerTokenResolver} 三处的重复常量定义。
 * </p>
 *
 * <h3>Cookie 属性策略</h3>
 * <ul>
 *     <li>{@code HttpOnly}:启用,禁止 JS 访问,防 XSS 窃取 Token</li>
 *     <li>{@code Path}:/{@code /},整站有效</li>
 *     <li>{@code Max-Age}:与 Token 过期时间对齐(access_token=30min,refresh_token=7d)</li>
 *     <li>{@code SameSite}:{@code Lax}——允许顶级导航跨站携带,保证 OAuth2 回调顺滑(决策 7)</li>
 *     <li>{@code Secure}:动态判断,HTTPS 才启用</li>
 * </ul>
 *
 * @author xuan
 */
public final class CookieConstant {

    private CookieConstant() {
    }

    /**
     * access_token Cookie 名称
     */
    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    /**
     * refresh_token Cookie 名称
     */
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    /**
     * access_token Cookie 存活时间:30 分钟(单位:秒)
     * <p>
     * 与 SAS 配置的 access_token 有效期保持一致。
     * </p>
     */
    public static final long ACCESS_TOKEN_TTL_SECONDS = 30 * 60L;

    /**
     * refresh_token Cookie 存活时间:7 天(单位:秒)
     * <p>
     * 与 SAS 配置的 refresh_token 有效期保持一致。7 天内无活动则 Cookie 失效。
     * </p>
     */
    public static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60L;

    /**
     * Cookie Path
     */
    public static final String COOKIE_PATH = "/";

    /**
     * Cookie SameSite 属性
     * <p>
     * 采用 {@code Lax}:允许顶级导航(GET 跳转)时跨站携带 Cookie,
     * 保证 GitHub/Gitee OAuth2 回调后第一次请求能拿到 Cookie。
     * 同时仍可阻挡 POST 跨站请求,CSRF 防护仍有效。
     * </p>
     */
    public static final String COOKIE_SAMESITE = "Lax";
}
