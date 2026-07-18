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

/**
 * OAuth2 授权同意记录
 * <p>
 * 对应 Spring Authorization Server 的 oauth2_authorization_consent 表，
 * 记录用户对某个客户端授予了哪些权限。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oauth2_authorization_consent")
public class OAuth2AuthorizationConsent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 客户端ID
    @TableId(type = IdType.INPUT)
    private String registeredClientId;

    // 用户标识
    private String principalName;

    // 授予的权限集合
    private String authorities;
}
