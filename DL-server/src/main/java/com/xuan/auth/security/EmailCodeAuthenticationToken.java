package com.xuan.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 邮箱认证 Token（博客端 grant_type=email_code）
 * <p>
 * 支持两种登录方式：
 * <ol>
 *     <li>邮箱 + 密码登录（已注册用户）</li>
 *     <li>邮箱 + 验证码登录（未注册用户自动注册为 GUEST）</li>
 * </ol>
 * 未认证状态：principal = email，credentials = password，code = 验证码
 * 已认证状态：principal = {@link SecurityUser}，credentials = null
 * </p>
 */
public class EmailCodeAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;   // 未认证时存 email，认证后存 UserDetails
    private final Object credentials; // 未认证时存 password
    private final String password;    // 密码（可能为空）
    private final String code;        // 邮箱验证码（可能为空）

    public EmailCodeAuthenticationToken(String email, String password, String code) {
        super(null);
        this.principal = email;
        this.credentials = password;
        this.password = password;
        this.code = code;
        setAuthenticated(false);
    }

    public EmailCodeAuthenticationToken(UserDetails userDetails, String password, String code,
                                        Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = userDetails;
        this.credentials = null;
        this.password = password;
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

    public String getPassword() {
        return password;
    }

    public String getCode() {
        return code;
    }
}
