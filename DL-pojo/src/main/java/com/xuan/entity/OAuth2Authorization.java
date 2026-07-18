package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OAuth2 授权记录
 * <p>
 * 对应 Spring Authorization Server 的 oauth2_authorization 表，
 * 记录每一次 OAuth2 授权流程，包括授权码、AccessToken、RefreshToken、ID Token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oauth2_authorization")
public class OAuth2Authorization implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 授权记录ID
    @TableId(type = IdType.INPUT)
    private String id;

    // 客户端ID
    private String registeredClientId;

    // 用户标识（username）
    private String principalName;

    // 授权类型
    private String authorizationGrantType;

    // 授权的作用域
    private String authorizedScopes;

    // 授权属性（序列化）
    private byte[] attributes;

    // 授权码模式中的 state 参数
    private String state;

    // 授权码值
    private byte[] authorizationCodeValue;

    // 授权码签发时间
    private LocalDateTime authorizationCodeIssuedAt;

    // 授权码过期时间
    private LocalDateTime authorizationCodeExpiresAt;

    // 授权码元数据
    private byte[] authorizationCodeMetadata;

    // AccessToken 值
    private byte[] accessTokenValue;

    // AccessToken 签发时间
    private LocalDateTime accessTokenIssuedAt;

    // AccessToken 过期时间
    private LocalDateTime accessTokenExpiresAt;

    // AccessToken 元数据
    private byte[] accessTokenMetadata;

    // Token 类型
    private String accessTokenType;

    // AccessToken 作用域
    private String accessTokenScopes;

    // OIDC ID Token 值
    private byte[] oidcIdTokenValue;

    // ID Token 签发时间
    private LocalDateTime oidcIdTokenIssuedAt;

    // ID Token 过期时间
    private LocalDateTime oidcIdTokenExpiresAt;

    // ID Token 元数据
    private byte[] oidcIdTokenMetadata;

    // RefreshToken 值
    private byte[] refreshTokenValue;

    // RefreshToken 签发时间
    private LocalDateTime refreshTokenIssuedAt;

    // RefreshToken 过期时间
    private LocalDateTime refreshTokenExpiresAt;

    // RefreshToken 元数据
    private byte[] refreshTokenMetadata;
}
