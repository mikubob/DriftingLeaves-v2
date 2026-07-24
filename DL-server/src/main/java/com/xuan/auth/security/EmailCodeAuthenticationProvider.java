package com.xuan.auth.security;

import com.xuan.entity.SysUser;
import com.xuan.mapper.OAuth2AuthorizationMapper;
import com.xuan.mapper.SysUserMapper;
import com.xuan.service.EmailCodeService;
import com.xuan.constant.MessageConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 邮箱认证提供者（博客端 grant_type=email_code）
 * <p>
 * 支持两种登录方式：
 * <ol>
 *     <li>邮箱 + 密码登录：已注册用户直接校验密码</li>
 *     <li>邮箱 + 验证码登录：已注册用户校验验证码后登录；未注册用户不再自动注册</li>
 * </ol>
 * 当请求同时提供 password 和 code 时，优先按密码登录处理。
 * </p>
 *
 * <h3>注册入口</h3>
 * <p>
 * 新用户需通过 {@code POST /blog/auth/register} 完成注册，设置密码后再使用邮箱验证码或密码登录。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCodeAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final EmailCodeService emailCodeService;
    private final SysUserMapper sysUserMapper;
    private final OAuth2AuthorizationMapper authorizationMapper;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends org.springframework.security.oauth2.core.OAuth2Token> tokenGenerator;

    /**
     * 开发模式固定验证码，仅在本地联调时使用
     */
    @Value("${dl.security.dev-code:}")
    private String devCode;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        EmailCodeAuthenticationToken token = (EmailCodeAuthenticationToken) authentication;

        String email = (String) token.getPrincipal();
        String password = token.getPassword();
        String code = token.getCode();

        // 1. 获取当前已认证的客户端
        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClient();
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (registeredClient == null) {
            throw new BadCredentialsException("客户端未认证");
        }

        // 2. 根据 email 查询 sys_user
        SysUser sysUser = sysUserMapper.selectByEmail(email);

        // 3. 区分登录方式：优先按密码登录处理
        SecurityUser user;
        boolean usePassword = StringUtils.hasText(password);
        if (usePassword) {
            // 邮箱 + 密码登录：用户必须已存在
            if (sysUser == null) {
                throw new BadCredentialsException(MessageConstant.EMAIL_NOT_REGISTERED);
            }
            if (sysUser.getStatus() == null || sysUser.getStatus() == 0) {
                throw new BadCredentialsException("账号已禁用，请联系管理员");
            }
            try {
                user = (SecurityUser) userDetailsService.loadUserByUsername(sysUser.getUsername());
            } catch (UsernameNotFoundException e) {
                throw new BadCredentialsException("用户不存在或已禁用");
            }
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("邮箱或密码错误");
            }
        } else {
            // 邮箱 + 验证码登录：用户必须已存在，不再自动注册
            if (sysUser == null) {
                throw new BadCredentialsException(MessageConstant.EMAIL_NOT_REGISTERED);
            }
            if (sysUser.getStatus() == null || sysUser.getStatus() == 0) {
                throw new BadCredentialsException("账号已禁用，请联系管理员");
            }
            if (!verifyCode(email, code)) {
                throw new BadCredentialsException("验证码错误或已过期");
            }
            try {
                user = (SecurityUser) userDetailsService.loadUserByUsername(sysUser.getUsername());
            } catch (UsernameNotFoundException e) {
                throw new BadCredentialsException("用户不存在或已禁用");
            }
        }

        // 4. 构建已认证 Token
        EmailCodeAuthenticationToken authenticatedToken = new EmailCodeAuthenticationToken(
                user, password, code, user.getAuthorities());

        // 5. 生成 Access Token
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

        // 6. 构建 Authorization 记录
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

        // 7. 生成 Refresh Token
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

        // 8. 登录互踢：同一账号在博客端只能保留一个有效授权
        removeOtherAuthorizations(user.getUsername(), registeredClient.getClientId());

        // 9. 保存授权记录
        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);

        // 10. 返回标准的 OAuth2AccessTokenAuthenticationToken
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal,
                accessToken, refreshToken, Collections.emptyMap());
    }

    /**
     * 登录互踢：删除同一用户在同一客户端下的其他授权记录
     */
    private void removeOtherAuthorizations(String principalName, String clientId) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.xuan.entity.OAuth2Authorization> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("principal_name", principalName)
                    .eq("registered_client_id", clientId);
            int deleted = authorizationMapper.delete(wrapper);
            if (deleted > 0) {
                log.info("博客端登录互踢: principalName={}, clientId={}, 删除旧授权 {} 条", principalName, clientId, deleted);
            }
        } catch (Exception e) {
            log.error("博客端登录互踢失败: principalName={}, clientId={}", principalName, clientId, e);
        }
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
     * </p>
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
