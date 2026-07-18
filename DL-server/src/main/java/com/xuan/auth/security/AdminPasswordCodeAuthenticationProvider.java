package com.xuan.auth.security;

import com.xuan.service.VerifyCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * 管理员用户名/密码/验证码认证提供者
 */
@Component
@RequiredArgsConstructor
public class AdminPasswordCodeAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator;

    /**
     * 开发模式固定验证码，仅在本地联调时使用
     */
    @Value("${dl.security.dev-code:}")
    private String devCode;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AdminPasswordCodeAuthenticationToken token = (AdminPasswordCodeAuthenticationToken) authentication;

        String username = (String) token.getPrincipal();
        String password = (String) token.getCredentials();
        String code = token.getCode();

        // 1. 获取当前已认证的客户端
        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClient();
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (registeredClient == null) {
            throw new BadCredentialsException("客户端未认证");
        }

        // 2. 加载用户
        SecurityUser user = (SecurityUser) userDetailsService.loadUserByUsername(username);

        // 3. 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 4. 校验邮箱验证码（管理员双因素认证）
        if (!verifyCode(user.getUserId(), code)) {
            throw new BadCredentialsException("验证码错误或已过期");
        }

        // 5. 构建已认证的用户 Token
        AdminPasswordCodeAuthenticationToken authenticatedToken = new AdminPasswordCodeAuthenticationToken(
                user, code, user.getAuthorities());

        // 6. 生成 Access Token
        Set<String> authorizedScopes = registeredClient.getScopes();
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(authenticatedToken)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(new AuthorizationGrantType("admin_password_code"))
                .authorizationGrant(authenticatedToken);

        // 生成 Access Token
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
                .principalName(username)
                .authorizationGrantType(new AuthorizationGrantType("admin_password_code"))
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
    private boolean verifyCode(Long userId, String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        // 开发模式：配置的固定验证码直接通过，便于本地联调
        if (StringUtils.hasText(devCode) && devCode.equals(code.trim())) {
            return true;
        }
        return verifyCodeService.verifyCode(userId, code.trim());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AdminPasswordCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
