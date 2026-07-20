package com.xuan.auth.security;

import com.xuan.entity.SysUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 第三方 OAuth2 登录成功处理器
 * <p>
 * 阶段四新增：GitHub/Gitee 登录成功后，将 OAuth2 身份转换为本项目标准的 JWT Token，
 * 写入 HttpOnly Cookie，并重定向到前端首页。
 * </p>
 *
 * <h3>为什么需要这个 Handler？</h3>
 * <p>
 * Spring Security 的 {@code oauth2Login()} 默认使用 Session 持久化认证状态，
 * 而本项目是 STATELESS + JWT 架构。因此 OAuth2 登录成功后必须：
 * </p>
 * <ol>
 *     <li>从 OAuth2User 中取出本地 sys_user 信息（由 CustomOAuth2UserService 写入）</li>
 *     <li>手动生成符合本项目规范的 JWT（含 roles / user_id / nickname claims）</li>
 *     <li>写入 access_token / refresh_token Cookie（与 OAuth2TokenResponseCookieHandler 格式一致）</li>
 *     <li>重定向到前端首页（前端从 Cookie 读取 Token 完成后续请求）</li>
 * </ol>
 *
 * <h3>JWT claims 与 OAuth2TokenResponseCookieHandler 保持一致</h3>
 * <ul>
 *     <li>{@code sub}：用户名（与 SAS 颁发的 Token 一致）</li>
 *     <li>{@code roles}：角色列表（带 ROLE_ 前缀）</li>
 *     <li>{@code user_id}：用户 ID</li>
 *     <li>{@code nickname}：昵称</li>
 *     <li>{@code iss}：http://localhost:5922</li>
 *     <li>{@code iat} / {@code exp}：标准时间戳</li>
 * </ul>
 *
 * <h3>RefreshToken 处理</h3>
 * <p>
 * 由于 SAS 的 RefreshToken 涉及 oauth2_authorization 表持久化，OAuth2 Login 流程不便复用。
 * 本处理器仅颁发 access_token（30 分钟），过期后前端需引导用户重新走 OAuth2 登录流程。
 * 如需 refresh_token，可后续扩展：在 oauth2_authorization 表中手动插入授权记录。
 * </p>
 *
 * @author xuan
 * @see CustomOAuth2UserService 提供 LOCAL_USER_ATTR_KEY / LOCAL_AUTHORITIES_ATTR_KEY
 * @see OAuth2TokenResponseCookieHandler Cookie 格式参考
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /**
     * access_token Cookie 名称（与 OAuth2TokenResponseCookieHandler 保持一致）
     */
    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    /**
     * access_token 有效期：30 分钟（与 SAS 配置一致）
     */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 30 * 60L;

    /**
     * JWT issuer（与 AuthorizationServerConfig.authorizationServerSettings 一致）
     */
    private static final String ISSUER = "http://localhost:5922";

    /**
     * JWT 编码器（SAS 提供的 Bean，复用同一密钥对签名）
     */
    private final JwtEncoder jwtEncoder;

    /**
     * 登录成功后重定向的前端 URL
     */
    @Value("${dl.oauth2-redirect.success-url:http://localhost:5173/}")
    private String successRedirectUrl;

    /**
     * 委托的 SimpleUrlAuthenticationSuccessHandler，用于在重定向前完成 SecurityContext 持久化等清理工作
     */
    private final SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 登录认证成功，开始生成 JWT Token: name={}", authentication.getName());

        // 1. 从 OAuth2User 中取出本地 sys_user 与角色（由 CustomOAuth2UserService 写入 attributes）
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        SysUser localUser = (SysUser) oAuth2User.getAttributes()
                .get(CustomOAuth2UserService.LOCAL_USER_ATTR_KEY);

        if (localUser == null) {
            log.error("OAuth2 登录成功但未找到本地 sys_user，attributes={}",
                    oAuth2User.getAttributes());
            response.sendRedirect(successRedirectUrl + "?error=oauth_user_not_found");
            return;
        }

        // 2. 构建 JWT claims
        //    与 JwtCustomizerConfig 注入的 claims 保持一致：sub / roles / user_id / nickname / iss / iat / exp
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ACCESS_TOKEN_TTL_SECONDS, ChronoUnit.SECONDS);

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(localUser.getUsername())
                .claim("roles", roles)
                .claim("user_id", localUser.getId())
                .claim("nickname", localUser.getNickname())
                .build();

        // 3. 编码生成 JWT
        String jwtTokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claimsSet))
                .getTokenValue();

        // 4. 写入 HttpOnly Cookie
        addTokenCookie(response, request, ACCESS_TOKEN_COOKIE_NAME,
                jwtTokenValue, ACCESS_TOKEN_TTL_SECONDS);

        log.info("OAuth2 登录 JWT 已生成并写入 Cookie: userId={}, username={}, roles={}",
                localUser.getId(), localUser.getUsername(), roles);

        // 5. 清除 SecurityContext（避免 Session 持有 OAuth2 认证信息，与 STATELESS 架构一致）
        //    注：由于 OAuth2LoginConfig 使用 IF_REQUIRED session 策略，session 仅临时存在
        //    重定向前清理认证状态，确保后续请求完全依赖 JWT Cookie
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        // 6. 重定向到前端首页
        response.sendRedirect(successRedirectUrl);
    }

    /**
     * 写入 Token Cookie
     * <p>
     * 与 OAuth2TokenResponseCookieHandler.addTokenCookie 保持一致的格式：
     * HttpOnly + Path=/ + SameSite=Strict + 动态 Secure
     * </p>
     */
    private void addTokenCookie(HttpServletResponse response, HttpServletRequest request,
                                String name, String value, long maxAgeSeconds) {
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        boolean isSecure = "https".equalsIgnoreCase(request.getScheme());

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
