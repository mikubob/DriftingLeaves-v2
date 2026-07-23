package com.xuan.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 自定义 grant_type=admin_password_code 请求转换器（管理端）
 * <p>
 * 管理端必须同时提供邮箱、密码、验证码三者。
 * </p>
 */
@Component
public class AdminPasswordCodeAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!"admin_password_code".equals(grantType)) {
            return null;
        }

        String email = request.getParameter("email");
        String password = request.getParameter(OAuth2ParameterNames.PASSWORD);
        String code = request.getParameter("code");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || !StringUtils.hasText(code)) {
            throw new OAuth2AuthenticationException("邮箱、密码、验证码均不能为空");
        }

        return new AdminPasswordCodeAuthenticationToken(email, password, code);
    }
}
