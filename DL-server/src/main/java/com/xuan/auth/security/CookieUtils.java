package com.xuan.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Cookie 工具类
 * <p>
 * 统一封装 Token Cookie 的写入、读取、清除逻辑。
 * 替代原本分散在 {@link OAuth2TokenResponseCookieHandler} 与 {@link OAuth2LoginSuccessHandler}
 * 中重复的 addTokenCookie 方法,以及 {@link com.xuan.resource.config.CookieBearerTokenResolver}
 * 中重复的常量定义。
 * </p>
 *
 * <h3>Cookie 格式</h3>
 * <pre>
 * access_token=&lt;urlencoded-jwt&gt;; Path=/; Max-Age=1800; HttpOnly; SameSite=Lax [; Secure]
 * </pre>
 *
 * <h3>使用场景</h3>
 * <ul>
 *     <li>SAS Token Endpoint 颁发 Token 时写 Cookie(原 OAuth2TokenResponseCookieHandler)</li>
 *     <li>OAuth2 第三方登录成功后写 Cookie(原 OAuth2LoginSuccessHandler)</li>
 *     <li>{@code /api/logout} 登出时清除 Cookie</li>
 *     <li>{@link com.xuan.resource.config.CookieBearerTokenResolver} 从请求中读取 access_token</li>
 * </ul>
 *
 * @author xuan
 */
public final class CookieUtils {

    private CookieUtils() {
    }

    /**
     * 写入 access_token Cookie
     *
     * @param response HTTP 响应
     * @param request  HTTP 请求(用于判断是否 HTTPS)
     * @param value    JWT Token 原始字符串
     */
    public static void addAccessTokenCookie(HttpServletResponse response, HttpServletRequest request,
                                            String value) {
        addTokenCookie(response, request, CookieConstant.ACCESS_TOKEN_COOKIE_NAME,
                value, CookieConstant.ACCESS_TOKEN_TTL_SECONDS);
    }

    /**
     * 写入 refresh_token Cookie
     *
     * @param response HTTP 响应
     * @param request  HTTP 请求(用于判断是否 HTTPS)
     * @param value    refresh_token 原始字符串
     */
    public static void addRefreshTokenCookie(HttpServletResponse response, HttpServletRequest request,
                                             String value) {
        addTokenCookie(response, request, CookieConstant.REFRESH_TOKEN_COOKIE_NAME,
                value, CookieConstant.REFRESH_TOKEN_TTL_SECONDS);
    }

    /**
     * 写入指定名称的 Token Cookie
     * <p>
     * Cookie 值会做 URL 编码(避免 JWT 中的特殊字符如 {@code .} {@code =} 破坏 Cookie 格式),
     * 读取时通过 {@link #resolveToken(HttpServletRequest, String)} 自动解码还原。
     * </p>
     *
     * @param response    HTTP 响应
     * @param request     HTTP 请求(用于判断协议)
     * @param name        Cookie 名称(access_token / refresh_token)
     * @param value       Cookie 值(JWT/refresh_token 原始字符串)
     * @param maxAgeSeconds 存活时间(秒)
     */
    public static void addTokenCookie(HttpServletResponse response, HttpServletRequest request,
                                      String name, String value, long maxAgeSeconds) {
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        boolean isSecure = "https".equalsIgnoreCase(request.getScheme());

        StringBuilder cookieBuilder = new StringBuilder()
                .append(name).append("=").append(encodedValue)
                .append("; Path=").append(CookieConstant.COOKIE_PATH)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly")
                .append("; SameSite=").append(CookieConstant.COOKIE_SAMESITE);
        if (isSecure) {
            cookieBuilder.append("; Secure");
        }

        response.addHeader("Set-Cookie", cookieBuilder.toString());
    }

    /**
     * 清除 access_token 与 refresh_token Cookie
     * <p>
     * 通过设置 Max-Age=0 立即过期,浏览器收到响应后会删除这两个 Cookie。
     * 用于 {@code /api/logout} 登出场景。
     * </p>
     *
     * @param response HTTP 响应
     */
    public static void clearTokenCookies(HttpServletResponse response) {
        clearCookie(response, CookieConstant.ACCESS_TOKEN_COOKIE_NAME);
        clearCookie(response, CookieConstant.REFRESH_TOKEN_COOKIE_NAME);
    }

    /**
     * 清除单个 Cookie
     *
     * @param response HTTP 响应
     * @param name     Cookie 名称
     */
    public static void clearCookie(HttpServletResponse response, String name) {
        StringBuilder cookieBuilder = new StringBuilder()
                .append(name).append("=")
                .append("; Path=").append(CookieConstant.COOKIE_PATH)
                .append("; Max-Age=0")
                .append("; HttpOnly")
                .append("; SameSite=").append(CookieConstant.COOKIE_SAMESITE);
        response.addHeader("Set-Cookie", cookieBuilder.toString());
    }

    /**
     * 从请求 Cookie 中读取指定名称的 Token 值
     * <p>
     * 自动 URL 解码(与写入时的编码对应)。
     * </p>
     *
     * @param request HTTP 请求
     * @param name    Cookie 名称
     * @return Token 字符串;Cookie 不存在时返回 null
     */
    public static String resolveToken(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
