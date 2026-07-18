package com.xuan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dl.jwt")
@Data
public class JwtProperties {
    /**
     * jwt令牌相关配置
     */
    private String secretKey;
    private Long ttl;
    private String tokenName;

    /**
     * Cookie 相关配置
     */
    private String cookieName = "dL9xK2mP5vQ8";
}
