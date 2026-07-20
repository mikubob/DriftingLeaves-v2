package com.xuan.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 邮箱验证码认证 Token（博客端 grant_type=email_code）
 * <p>
 * 未认证状态：principal = email，credentials = code
 * 已认证状态：principal = {@link SecurityUser}，credentials = null
 * </p>
 */
public class EmailCodeAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;   // 未认证时存 email，认证后存 UserDetails
    private final Object credentials; // 未认证时存验证码 code
    private final String code;        // 邮箱验证码（与 credentials 同值，保留以与管理端 Token 风格一致）

    public EmailCodeAuthenticationToken(String email, String code) {
        super(null);
        this.principal = email;
        this.credentials = code;
        this.code = code;
        setAuthenticated(false);
    }

    public EmailCodeAuthenticationToken(UserDetails userDetails, String code,
                                        Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = userDetails;
        this.credentials = null;
        this.code = code;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getCode() {
        return code;
    }
}
