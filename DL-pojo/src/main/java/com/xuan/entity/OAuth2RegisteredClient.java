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
 * OAuth2 客户端注册信息
 * <p>
 * 对应 Spring Authorization Server 的 oauth2_registered_client 表，
 * 存储所有 OAuth2 客户端配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oauth2_registered_client")
public class OAuth2RegisteredClient implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 客户端ID（UUID）
    @TableId(type = IdType.INPUT)
    private String id;

    // 客户端标识，如 blog-client
    private String clientId;

    // 客户端注册时间
    private LocalDateTime clientIdIssuedAt;

    // 客户端密钥（BCrypt加密）
    private String clientSecret;

    // 客户端密钥过期时间
    private LocalDateTime clientSecretExpiresAt;

    // 客户端名称
    private String clientName;

    // 认证方式：client_secret_basic, client_secret_post, none
    private String clientAuthenticationMethods;

    // 授权类型：authorization_code, refresh_token, password
    private String authorizationGrantTypes;

    // 授权码模式回调地址
    private String redirectUris;

    // 登出回调地址
    private String postLogoutRedirectUris;

    // 作用域：openid, profile, read, write
    private String scopes;

    // 客户端设置（JSON）
    private String clientSettings;

    // Token设置（JSON）
    private String tokenSettings;
}
