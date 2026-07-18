package com.xuan.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 管理员用户名/密码/验证码认证 Token
 */
public class AdminPasswordCodeAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;   // 未认证时存 username，认证后存 UserDetails
    private final Object credentials; // 未认证时存 password
    private final String code;        // 邮箱验证码

    public AdminPasswordCodeAuthenticationToken(String username, String password, String code) {
        super(null);
        this.principal = username;
        this.credentials = password;
        this.code = code;
        setAuthenticated(false);
    }

    public AdminPasswordCodeAuthenticationToken(UserDetails userDetails, String code,
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
