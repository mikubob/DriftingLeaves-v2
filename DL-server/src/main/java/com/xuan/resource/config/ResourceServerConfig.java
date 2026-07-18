package com.xuan.resource.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuan.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 Resource Server 配置类
 * 负责 JWT Token 校验、权限映射以及 /admin/** 接口的访问控制
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class ResourceServerConfig {

    private final ObjectMapper objectMapper;

    /**
     * Resource Server 安全过滤器链
     * 处理非授权服务器端点的 JWT 鉴权
     */
    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new CustomAccessDeniedHandler(objectMapper))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/admin/admin/login", "/admin/admin/sendCode", "/admin/admin/logout").permitAll()
                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /**
     * JWT 权限转换器
     * 从 JWT 的 roles claim 提取权限，并添加 ROLE_ 前缀
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    /**
     * 未认证异常处理：返回 401 JSON
     */
    public static class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException, ServletException {
            log.warn("未认证访问: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), Result.error("未认证，请先登录"));
        }
    }

    /**
     * 权限不足异常处理：返回 403 JSON
     */
    public static class CustomAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper objectMapper;

        public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException, ServletException {
            log.warn("权限不足: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), Result.error("权限不足"));
        }
    }
}
