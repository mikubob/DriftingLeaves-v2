package com.xuan.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;

/**
 * 自定义 OAuth2 授权请求解析器
 * <p>
 * P1-3 新增:支持前端通过 {@code state} 参数传递 {@code redirect_uri}(多前端源回跳,决策 4)。
 * </p>
 *
 * <h3>为什么需要自定义 Resolver?</h3>
 * <p>
 * Spring Security 默认的 {@link DefaultOAuth2AuthorizationRequestResolver} 会自行生成
 * 随机 {@code state}(用于 CSRF 防护),忽略前端传入的 {@code state} 参数。
 * 若要实现"前端在 state 中编码 redirect_uri",必须让 Spring Security 保留前端传的 state。
 * </p>
 *
 * <h3>实现方式</h3>
 * <p>
 * 包装 {@link DefaultOAuth2AuthorizationRequestResolver},在默认解析完成后,
 * 检查请求是否携带 {@code state} 参数:
 * </p>
 * <ul>
 *     <li>有 → 用前端传的 state 覆盖默认生成的 state(SPRING SECURITY 会保存和验证此值)</li>
 *     <li>无 → 使用默认生成的 state(保持原有行为不变)</li>
 * </ul>
 *
 * <h3>前端调用示例</h3>
 * <pre>
 * GET /oauth2/authorization/github?state=&lt;base64({"redirect_uri":"http://localhost:5174/#/login"})&gt;
 * </pre>
 *
 * <h3>安全性说明</h3>
 * <p>
 * Spring Security 仍会验证 state 的一致性(发起时与回调时必须相同),CSRF 防护仍然有效。
 * 前端传的 state 值可以是 base64 编码的 JSON,只要回调时一致即可。
 * 后端在 SuccessHandler 中解码 state 取 redirect_uri,经白名单校验后跳转。
 * </p>
 *
 * @author xuan
 * @see OAuth2LoginSuccessHandler 解码 state 取 redirect_uri 的逻辑
 */
public class OAuth2StateAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /**
     * OAuth2 授权请求基础 URI(与 Spring Security 默认值一致)
     */
    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    /**
     * 委托的默认解析器
     */
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    /**
     * 构造函数
     *
     * @param clientRegistrationRepository 客户端注册信息仓库
     */
    public OAuth2StateAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request);
        return overrideStateIfPresent(request, resolved);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request, clientRegistrationId);
        return overrideStateIfPresent(request, resolved);
    }

    /**
     * 如果请求中携带 state 参数,用前端传的 state 覆盖默认生成的 state
     * <p>
     * 这样 Spring Security 会把前端传的 state 保存到 AuthorizationRequestRepository,
     * 并在回调时验证一致性。SuccessHandler 可通过 {@code request.getParameter("state")}
     * 取到此值进行解码。
     * </p>
     *
     * @param request   HTTP 请求
     * @param resolved  默认解析器解析出的 OAuth2AuthorizationRequest
     * @return 可能被覆盖 state 的 OAuth2AuthorizationRequest;若 resolved 为 null 则返回 null
     */
    private OAuth2AuthorizationRequest overrideStateIfPresent(HttpServletRequest request,
                                                              OAuth2AuthorizationRequest resolved) {
        if (resolved == null) {
            return null;
        }
        String stateParam = request.getParameter("state");
        if (StringUtils.hasText(stateParam)) {
            return OAuth2AuthorizationRequest.from(resolved)
                    .state(stateParam)
                    .build();
        }
        return resolved;
    }
}
