package com.xuan.resource.config;

import com.xuan.auth.security.CookieConstant;
import com.xuan.auth.security.CookieUtils;
import com.xuan.auth.security.OAuth2TokenResponseCookieHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

/**
 * 自定义 Bearer Token 解析器:支持 Header + Cookie 双轨模式
 * <p>
 * Token Cookie 下发机制的核心组件之一。
 * Spring Security Resource Server 默认仅从 {@code Authorization: Bearer xxx} Header 解析 Token,
 * 本解析器在此基础上扩展支持从 {@code access_token} Cookie 解析,实现双轨兼容。
 * </p>
 *
 * <h3>解析优先级</h3>
 * <ol>
 *     <li>优先:{@code Authorization: Bearer xxx} Header(兼容旧的前端 Header 模式)</li>
 *     <li>其次:{@code access_token} Cookie(浏览器自动携带,简化前端开发)</li>
 *     <li>两者都没有:返回 null(后续由 OAuth2ResourceServer 触发 401 AuthenticationEntryPoint)</li>
 * </ol>
 *
 * <h3>Cookie 编码说明</h3>
 * <p>
 * 写入 Cookie 时使用 URLEncoder.encode 编码(避免 JWT 中的特殊字符如 . = 破坏 Cookie 格式),
 * 解析时通过 {@link CookieUtils#resolveToken} 自动 URLDecoder.decode 解码还原。
 * </p>
 *
 * <h3>使用方式</h3>
 * <p>
 * 在 {@link ResourceServerConfig} 中通过 {@code .oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(...))}
 * 注册本解析器。
 * </p>
 *
 * @see OAuth2TokenResponseCookieHandler 写入 Cookie 的对应处理器
 * @see CookieConstant Cookie 名称常量
 * @see CookieUtils Cookie 读取工具类
 * @see DefaultBearerTokenResolver Spring Security 默认的 Header 解析器
 */
@Slf4j
public class CookieBearerTokenResolver implements BearerTokenResolver {

    /**
     * Spring Security 默认的 Header 解析器(委托给它的 resolve 方法解析 Authorization Header)
     */
    private final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

    /**
     * 从当前请求解析 Bearer Token
     * <p>
     * 解析顺序:Header → Cookie。一旦在某一层解析到 Token,立即返回,不再继续。
     * </p>
     *
     * @param request HTTP 请求
     * @return Token 字符串;无 Token 返回 null
     */
    @Override
    public String resolve(HttpServletRequest request) {
        // 第一优先级:从 Authorization Header 解析(兼容 Header 模式)
        String token = defaultResolver.resolve(request);
        if (StringUtils.hasText(token)) {
            return token;
        }

        // 第二优先级:从 access_token Cookie 解析(浏览器自动携带场景)
        // Cookie 读取与解码逻辑统一委托给 CookieUtils
        String cookieToken = CookieUtils.resolveToken(request, CookieConstant.ACCESS_TOKEN_COOKIE_NAME);
        if (StringUtils.hasText(cookieToken)) {
            log.debug("从 Cookie 解析到 access_token, 长度={}", cookieToken.length());
            return cookieToken;
        }
        return null;
    }
}
