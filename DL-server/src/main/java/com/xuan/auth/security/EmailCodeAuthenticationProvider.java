package com.xuan.auth.security;

import com.xuan.entity.SysUser;
import com.xuan.mapper.SysUserMapper;
import com.xuan.service.EmailCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * 邮箱验证码认证提供者（博客端 grant_type=email_code）
 * <p>
 * 流程：
 * <ol>
 *     <li>校验邮箱验证码（按 email 维度，由 {@link EmailCodeService} 处理）</li>
 *     <li>根据 email 加载 sys_user（不存在则报错，提示先注册）</li>
 *     <li>委托 {@link UserDetailsService} 加载完整用户信息（含角色）</li>
 *     <li>生成 access_token + refresh_token，保存授权记录</li>
 * </ol>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class EmailCodeAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final EmailCodeService emailCodeService;
    private final SysUserMapper sysUserMapper;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator;

    /**
     * 开发模式固定验证码，仅在本地联调时使用
     */
    @Value("${dl.security.dev-code:}")
    private String devCode;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        EmailCodeAuthenticationToken token = (EmailCodeAuthenticationToken) authentication;

        String email = (String) token.getPrincipal();
        String code = token.getCode();

        // 1. 获取当前已认证的客户端
        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClient();
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (registeredClient == null) {
            throw new BadCredentialsException("客户端未认证");
        }

        // 2. 校验邮箱验证码
        if (!verifyCode(email, code)) {
            throw new BadCredentialsException("验证码错误或已过期");
        }

        // 3. 根据 email 查询 sys_user，不存在则报错（提示先注册）
        SysUser sysUser = sysUserMapper.selectByEmail(email);
        if (sysUser == null || sysUser.getStatus() == null || sysUser.getStatus() == 0) {
            throw new BadCredentialsException("用户不存在或已禁用，请先注册");
        }

        // 4. 委托 UserDetailsService 加载完整用户信息（含角色权限）
        SecurityUser user;
        try {
            user = (SecurityUser) userDetailsService.loadUserByUsername(sysUser.getUsername());
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("用户不存在或已禁用，请先注册");
        }

        // 5. 构建已认证 Token
        EmailCodeAuthenticationToken authenticatedToken = new EmailCodeAuthenticationToken(
                user, code, user.getAuthorities());

        // 6. 生成 Access Token
        Set<String> authorizedScopes = registeredClient.getScopes();
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(authenticatedToken)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(new AuthorizationGrantType("email_code"))
                .authorizationGrant(authenticatedToken);

        OAuth2TokenContext tokenContext = tokenContextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();
        org.springframework.security.oauth2.core.OAuth2Token generatedAccessToken = tokenGenerator.generate(tokenContext);
        if (generatedAccessToken == null) {
            throw new BadCredentialsException("无法生成 Access Token");
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(),
                generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(),
                authorizedScopes);

        // 7. 构建 Authorization 记录
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(user.getUsername())
                .authorizationGrantType(new AuthorizationGrantType("email_code"))
                .authorizedScopes(authorizedScopes)
                .attribute(Principal.class.getName(), authenticatedToken)
                .token(accessToken, metadata -> {
                    if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
                        metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims());
                    }
                    metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, false);
                });

        // 8. 生成 Refresh Token
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2TokenContext refreshTokenContext = tokenContextBuilder
                    .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                    .build();
            org.springframework.security.oauth2.core.OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshTokenContext);
            if (generatedRefreshToken != null) {
                refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
                authorizationBuilder.refreshToken(refreshToken);
            }
        }

        // 9. 保存授权记录
        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);

        // 10. 返回标准的 OAuth2AccessTokenAuthenticationToken
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal,
                accessToken, refreshToken, Collections.emptyMap());
    }

    /**
     * 获取已认证的客户端信息
     */
    private OAuth2ClientAuthenticationToken getAuthenticatedClient() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2ClientAuthenticationToken clientToken && clientToken.isAuthenticated()) {
            return clientToken;
        }
        throw new BadCredentialsException("客户端未认证");
    }

    /**
     * 验证码校验
     * <p>
     * 生产环境走 Redis 校验；开发环境若配置了 dl.security.dev-code，则匹配时直接通过
     */
    private boolean verifyCode(String email, String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        if (StringUtils.hasText(devCode) && devCode.equals(code.trim())) {
            return true;
        }
        return emailCodeService.verifyCode(email, code.trim());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return EmailCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
