package com.xuan.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 签名密钥配置
 * <p>
 * 固定 RSA 密钥对配置，避免每次应用重启后重新生成密钥导致旧 token 失效。
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dl.security.jwk")
public class JwkProperties {

    /**
     * RSA 公钥（PEM 格式，X.509 SubjectPublicKeyInfo）
     */
    private String publicKey;

    /**
     * RSA 私钥（PEM 格式，PKCS#8）
     */
    private String privateKey;

    /**
     * 密钥 ID，用于 JWKS 端点标识
     */
    private String keyId = "driftingleaves-fixed-key";
}
