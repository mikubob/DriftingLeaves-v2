package com.xuan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第三方 OAuth2 登录配置属性
 * <p>
 * 阶段四新增：替代 Spring Boot 自动配置 {@code spring.security.oauth2.client.*}，
 * 避免在 client-id 为空时启动失败（开发期未配置第三方凭证时也能正常启动）。
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * dl:
 *   oauth2:
 *     github:
 *       client-id: "your-github-client-id"
 *       client-secret: "your-github-client-secret"
 *     gitee:
 *       client-id: "your-gitee-client-id"
 *       client-secret: "your-gitee-client-secret"
 *   oauth2-redirect:
 *     success-url: http://localhost:5173/
 *     failure-url: http://localhost:5173/login
 * </pre>
 *
 * <h3>启用条件</h3>
 * <ul>
 *     <li>{@code dl.oauth2.github.client-id} 非空 → 注册 GitHub ClientRegistration</li>
 *     <li>{@code dl.oauth2.gitee.client-id} 非空 → 注册 Gitee ClientRegistration</li>
 *     <li>两者都为空 → 不注册任何 ClientRegistration，OAuth2 登录功能不可用，但应用正常启动</li>
 * </ul>
 *
 * @author xuan
 */
@Component
@ConfigurationProperties(prefix = "dl.oauth2")
@Data
public class OAuth2LoginProperties {

    /**
     * GitHub OAuth2 客户端配置
     */
    private ClientConfig github = new ClientConfig();

    /**
     * Gitee OAuth2 客户端配置
     */
    private ClientConfig gitee = new ClientConfig();

    /**
     * 单个第三方平台的客户端配置
     */
    @Data
    public static class ClientConfig {
        /**
         * 第三方平台分配的 Client ID
         */
        private String clientId = "";

        /**
         * 第三方平台分配的 Client Secret
         */
        private String clientSecret = "";

        /**
         * 是否已配置（client-id 和 client-secret 均非空时才视为已配置）
         *
         * @return true 表示已配置，可注册 ClientRegistration
         */
        public boolean isConfigured() {
            return clientId != null && !clientId.trim().isEmpty()
                    && clientSecret != null && !clientSecret.trim().isEmpty();
        }
    }

    /**
     * GitHub 是否已配置
     */
    public boolean isGithubConfigured() {
        return github.isConfigured();
    }

    /**
     * Gitee 是否已配置
     */
    public boolean isGiteeConfigured() {
        return gitee.isConfigured();
    }

    /**
     * 是否有任意一个第三方平台已配置
     */
    public boolean hasAnyConfigured() {
        return isGithubConfigured() || isGiteeConfigured();
    }
}
