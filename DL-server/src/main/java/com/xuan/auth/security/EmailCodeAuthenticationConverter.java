package com.xuan.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 自定义 grant_type=email_code 请求转换器（博客端）
 * <p>
 * 支持两种登录方式：
 * <ol>
 *     <li>邮箱 + 密码登录：email + password</li>
 *     <li>邮箱 + 验证码登录：email + code</li>
 * </ol>
 * 二者至少提供一种完整凭证，password 优先。
 * </p>
 */
@Component
public class EmailCodeAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!"email_code".equals(grantType)) {
            return null;
        }

        String email = request.getParameter("email");
        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException("邮箱不能为空");
        }

        String password = request.getParameter(OAuth2ParameterNames.PASSWORD);
        String code = request.getParameter("code");

        if (!StringUtils.hasText(password) && !StringUtils.hasText(code)) {
            throw new OAuth2AuthenticationException("密码和验证码至少填写一项");
        }

        return new EmailCodeAuthenticationToken(email, password, code);
    }
}
