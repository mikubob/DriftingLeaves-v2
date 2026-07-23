package com.xuan.auth.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;

/**
 * 封装登录用户信息的 Security 用户对象
 * <p>
 * 支持 Jackson 序列化/反序列化，用于 JdbcOAuth2AuthorizationService 持久化 OAuth2Authorization。
 * </p>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityUser implements UserDetails, Serializable {

    private Long userId;
    private String username;
    private String password;
    private Integer userType;
    private String nickname;
    // 邮箱:供 /me 接口、JWT claims 使用
    private String email;
    // 头像 URL:供 /me 接口、JWT claims 使用
    private String avatar;
    private Collection<? extends GrantedAuthority> authorities;

    @JsonCreator
    public static SecurityUser of(
            @JsonProperty("userId") Long userId,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("userType") Integer userType,
            @JsonProperty("nickname") String nickname,
            @JsonProperty("email") String email,
            @JsonProperty("avatar") String avatar,
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities) {
        return new SecurityUser(userId, username, password, userType, nickname, email, avatar, authorities);
    }

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
