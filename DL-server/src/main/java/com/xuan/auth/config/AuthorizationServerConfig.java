package com.xuan.auth.config;

import com.xuan.auth.security.AdminPasswordCodeAuthenticationConverter;
import com.xuan.auth.security.AdminPasswordCodeAuthenticationProvider;
import com.xuan.auth.security.OAuth2TokenResponseCookieHandler;
import com.xuan.auth.util.Jwks;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AccessTokenResponseAuthenticationSuccessHandler;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Authorization Server 配置类
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    /**
     * 密码编码器
     * 使用 DelegatingPasswordEncoder，支持多种密码编码格式（如 {bcrypt} 前缀）
     * SAS 客户端认证需要带 {bcrypt} 前缀，用户密码验证也能兼容 BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 授权服务器安全过滤器链
     * <p>
     * 通过 @Order(1) 优先级高于 Resource Server 链，确保 /oauth2/** 端点优先被本链处理。
     * </p>
     *
     * <h3>核心配置</h3>
     * <ul>
     *     <li>{@code applyDefaultSecurity(http)}：注册 SAS 默认安全配置（含 OIDC 端点）</li>
     *     <li>{@code tokenEndpoint}：注册自定义 grant_type 的转换器和认证提供者</li>
     *     <li>{@code accessTokenResponseHandler}：注册 Token Cookie 下发处理器（步骤 6 新增）</li>
     * </ul>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      AdminPasswordCodeAuthenticationConverter adminPasswordCodeAuthenticationConverter,
                                                                      AdminPasswordCodeAuthenticationProvider adminPasswordCodeAuthenticationProvider) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults())
                // 注册自定义 grant type 的转换器和认证提供者
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                        .accessTokenRequestConverter(adminPasswordCodeAuthenticationConverter)
                        .authenticationProvider(adminPasswordCodeAuthenticationProvider)
                        // 注册 Token 响应 Cookie 处理器：在默认 JSON 响应基础上追加 HttpOnly Cookie 下发
                        // 包装模式：OAuth2TokenResponseCookieHandler 内部委托给默认的
                        // OAuth2AccessTokenResponseAuthenticationSuccessHandler 输出 JSON 响应体
                        .accessTokenResponseHandler(new OAuth2TokenResponseCookieHandler(
                                new OAuth2AccessTokenResponseAuthenticationSuccessHandler()))
                );
        return http.build();
    }

    /**
     * 客户端注册信息存储
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * 授权记录存储
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * 授权同意记录存储
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                         RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * JWK 源，用于签名和 JWKS 端点
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = Jwks.generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, context) -> jwkSelector.select(jwkSet);
    }

    /**
     * JWT 编码器
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * JWT 解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Token 生成器
     */
    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder,
                                                  ObjectProvider<OAuth2TokenCustomizer<JwtEncodingContext>> jwtCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtCustomizer.ifAvailable(jwtGenerator::setJwtCustomizer);
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, refreshTokenGenerator);
    }

    /**
     * 授权服务器 issuer 设置
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:5922")
                .build();
    }
}
