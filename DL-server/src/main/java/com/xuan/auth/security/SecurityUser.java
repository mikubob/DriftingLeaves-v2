package com.xuan.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 封装登录用户信息的 Security 用户对象
 */
@Getter
@AllArgsConstructor
public class SecurityUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Integer userType;
    private final String nickname;
    // 邮箱:供 /me 接口、JWT claims 使用
    private final String email;
    // 头像 URL:供 /me 接口、JWT claims 使用
    private final String avatar;
    private final Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
