package com.xuan.controller;

import com.xuan.auth.security.CookieConstant;
import com.xuan.auth.security.CookieUtils;
import com.xuan.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登出接口(Admin/Blog 共用)
 * <p>
 * 决策 2 方案 A:新增包装接口 {@code POST /api/logout},前端无需传 token。
 * Token 存在 HttpOnly Cookie 里,前端 JS 读不到,因此由后端从 Cookie 读取并吊销。
 * </p>
 *
 * <h3>处理流程</h3>
 * <ol>
 *     <li>从 Cookie 读 access_token / refresh_token(可能为空)</li>
 *     <li>若存在,调用 {@link OAuth2AuthorizationService#remove} 失效 token</li>
 *     <li>无论 token 是否有效,都清空浏览器 Cookie</li>
 *     <li>返回 success</li>
 * </ol>
 *
 * <h3>鉴权策略</h3>
 * <p>
 * {@code permitAll}——不强制要求有效 access_token。
 * 即使 Cookie 中 token 已过期或不存在,也允许调用(否则前端遇到 token 过期时无法登出,
 * 会陷入"token 过期 → 调 logout 失败 → 无法清 Cookie → 卡死"的死循环)。
 * </p>
 *
 * <h3>权限配置</h3>
 * <p>
 * 在 {@code ResourceServerConfig} 中将 {@code /api/logout} 设为 permitAll,
 * 且必须放在 {@code /admin/**} 规则之前(否则会被 hasAnyRole 拦截)。
 * </p>
 *
 * @author xuan
 */
@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class LogoutController {

    private final OAuth2AuthorizationService authorizationService;

    /**
     * 登出
     * <p>
     * 从 Cookie 读 access_token / refresh_token,调用 OAuth2AuthorizationService 失效,
     * 然后清空浏览器 Cookie。无论 token 是否有效,都返回 success 并清 Cookie。
     * </p>
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. 从 Cookie 读 access_token,吊销
        String accessToken = CookieUtils.resolveToken(request, CookieConstant.ACCESS_TOKEN_COOKIE_NAME);
        if (accessToken != null) {
            OAuth2Authorization authorization = authorizationService.findByToken(
                    accessToken, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization != null) {
                authorizationService.remove(authorization);
                log.info("登出:access_token 已吊销: tokenPrefix={}",
                        accessToken.substring(0, Math.min(20, accessToken.length())));
            } else {
                log.info("登出:access_token 在 oauth2_authorization 表中未找到(可能已过期或为 OAuth2 Login 颁发)");
            }
        }

        // 2. 从 Cookie 读 refresh_token,吊销
        //    注意:access_token 吊销时通常已连带移除整条 authorization 记录(含 refresh_token),
        //    这里再做一次 refresh_token 查找是为了兼容 access_token 已过期、refresh_token 仍有效的场景
        String refreshToken = CookieUtils.resolveToken(request, CookieConstant.REFRESH_TOKEN_COOKIE_NAME);
        if (refreshToken != null) {
            OAuth2Authorization authorization = authorizationService.findByToken(
                    refreshToken, OAuth2TokenType.REFRESH_TOKEN);
            if (authorization != null) {
                authorizationService.remove(authorization);
                log.info("登出:refresh_token 已吊销");
            }
        }

        // 3. 无论 token 是否有效,都清空 Cookie
        CookieUtils.clearTokenCookies(response);

        log.info("登出成功:已清空 access_token / refresh_token Cookie");
        return Result.success();
    }
}
