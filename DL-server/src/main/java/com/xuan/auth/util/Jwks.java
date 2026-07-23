package com.xuan.auth.util;

import com.nimbusds.jose.jwk.RSAKey;
import com.xuan.properties.JwkProperties;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * JWT 密钥工具类
 */
public final class Jwks {

    private Jwks() {
    }

    /**
     * 从配置中的 PEM 密钥构建 RSAKey
     *
     * @param properties 固定密钥配置
     * @return RSAKey
     */
    public static RSAKey loadRsaKey(JwkProperties properties) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) parsePublicKey(properties.getPublicKey());
            RSAPrivateKey privateKey = (RSAPrivateKey) parsePrivateKey(properties.getPrivateKey());
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(properties.getKeyId())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("加载固定 RSA 密钥失败，请检查 dl.security.jwk.public-key / private-key 配置", e);
        }
    }

    /**
     * 生成 RSA 密钥对并封装为 RSAKey
     */
    public static RSAKey generateRsa() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    /**
     * 生成 PEM 格式的 RSA 密钥对（用于首次生成并写入配置）
     */
    public static String[] generateRsaPemPair() {
        KeyPair keyPair = generateRsaKey();
        String publicKeyPem = encodeToPem(keyPair.getPublic().getEncoded(), "PUBLIC KEY");
        String privateKeyPem = encodeToPem(keyPair.getPrivate().getEncoded(), "PRIVATE KEY");
        return new String[]{publicKeyPem, privateKeyPem};
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("生成 RSA 密钥对失败", e);
        }
    }

    private static PublicKey parsePublicKey(String publicKeyPem) throws Exception {
        byte[] encoded = parsePem(publicKeyPem, "PUBLIC KEY");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    private static PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        byte[] encoded = parsePem(privateKeyPem, "PRIVATE KEY");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private static byte[] parsePem(String pem, String label) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM 密钥不能为空: " + label);
        }
        String stripped = pem.replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }

    private static String encodeToPem(byte[] encoded, String label) {
        String base64 = Base64.getEncoder().encodeToString(encoded);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(label).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append("-----END ").append(label).append("-----");
        return sb.toString();
    }
}
