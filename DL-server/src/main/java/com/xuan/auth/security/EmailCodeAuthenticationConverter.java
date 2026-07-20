package com.xuan.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 自定义 grant_type=email_code 请求转换器
 * <p>
 * 从 {@code POST /oauth2/token} 请求中提取 email 与 code 参数，
 * 构造 {@link EmailCodeAuthenticationToken} 交给 {@link EmailCodeAuthenticationProvider} 认证。
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
        String code = request.getParameter("code");

        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new OAuth2AuthenticationException("邮箱和验证码不能为空");
        }

        return new EmailCodeAuthenticationToken(email, code);
    }
}
