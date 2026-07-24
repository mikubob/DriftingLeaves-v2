package com.xuan.auth.security;

import com.xuan.constant.MessageConstant;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysUserRole;
import com.xuan.exception.BaseException;
import com.xuan.mapper.SysUserMapper;
import com.xuan.mapper.SysUserRoleMapper;
import com.xuan.util.UsernameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第三方 OAuth2 登录用户信息服务
 * <p>
 * 处理 GitHub/Gitee 登录回调，将第三方用户信息映射到本地 sys_user。
 * </p>
 *
 * <h3>核心职责</h3>
 * <ol>
 *     <li>从 OAuth2 提供商拉取用户信息（GitHub/Gitee 字段差异较大）</li>
 *     <li>查找或创建本地 sys_user 记录</li>
 *     <li>邮箱相同时自动绑定到已有账号（用户决策点）</li>
 *     <li>返回带 OAuthProviderUserContext 的 OAuth2User 供后续 SuccessHandler 使用</li>
 * </ol>
 *
 * <h3>用户查找/创建策略</h3>
 * <pre>
 * 1. 按 (oauth_provider, oauth_id) 查找
 *    ├─ 找到 → 直接返回（已绑定过）
 *    └─ 未找到 → 进入第 2 步
 * 2. 按 email 查找（仅当第三方返回 email 非空时）
 *    ├─ 找到 → 绑定到已有账号（更新 oauth_provider、oauth_id、login_type、avatar）
 *    └─ 未找到 → 进入第 3 步
 * 3. 自动创建新 sys_user
 *    ├─ user_type=1（博客用户）
 *    ├─ status=1（启用）
 *    ├─ login_type=2(GitHub) / 3(Gitee)
 *    ├─ username = provider + "_" + oauthId（保证唯一）
 *    ├─ password = 随机 UUID BCrypt 占位（第三方登录不会用到密码）
 *    ├─ email = 第三方返回的 email（可能为空）
 *    ├─ avatar = 第三方返回的 avatar_url
 *    └─ 关联 GUEST 角色
 * </pre>
 *
 * <h3>GitHub vs Gitee 字段差异</h3>
 * <table border="1">
 *     <tr><th>字段</th><th>GitHub</th><th>Gitee</th></tr>
 *     <tr><td>用户 ID</td><td>id</td><td>id</td></tr>
 *     <tr><td>用户名</td><td>login</td><td>login</td></tr>
 *     <tr><td>邮箱</td><td>email（私密时为 null）</td><td>email</td></tr>
 *     <tr><td>头像</td><td>avatar_url</td><td>avatar_url</td></tr>
 * </table>
 *
 * <h3>登录类型映射</h3>
 * <ul>
 *     <li>github → login_type=2</li>
 *     <li>gitee → login_type=3</li>
 * </ul>
 *
 * @author xuan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    /**
     * OAuth2User attributes 中存储本地 sys_user 的 Key
     * <p>
     * SuccessHandler 通过此 Key 从 OAuth2User attributes 中取出本地用户信息。
     * </p>
     */
    public static final String LOCAL_USER_ATTR_KEY = "local_sys_user";

    /**
     * OAuth2User attributes 中存储角色权限集合的 Key
     */
    public static final String LOCAL_AUTHORITIES_ATTR_KEY = "local_authorities";

    /**
     * OAuth2User attributes 中存储 OAuth 平台标识的 Key
     */
    public static final String OAUTH_PROVIDER_ATTR_KEY = "oauth_provider";

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 调用父类拉取第三方用户信息
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2. 识别 OAuth 平台
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        log.info("OAuth2 登录回调: provider={}, attributes={}", provider, attributes);

        // 3. 提取平台无关的字段（id、login、name、email、avatar_url）
        String oauthId = String.valueOf(attributes.get("id"));
        String login = (String) attributes.get("login");
        String name = (String) attributes.get("name");
        String email = (String) attributes.get("email");
        String avatarUrl = (String) attributes.get("avatar_url");

        // 4. 查找或创建本地 sys_user
        SysUser localUser = findOrCreateLocalUser(provider, oauthId, email, avatarUrl);

        // 5. 加载用户角色
        List<String> roleCodes = sysUserRoleMapper.selectRoleCodesByUserId(localUser.getId());
        Collection<? extends GrantedAuthority> authorities = roleCodes.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        // 6. 构造返回的 OAuth2User（携带本地用户信息，供 SuccessHandler 使用）
        Map<String, Object> enrichedAttributes = new HashMap<>(attributes);
        enrichedAttributes.put(LOCAL_USER_ATTR_KEY, localUser);
        enrichedAttributes.put(LOCAL_AUTHORITIES_ATTR_KEY, authorities);
        enrichedAttributes.put(OAUTH_PROVIDER_ATTR_KEY, provider);

        // principalName 使用本地 sys_user.id，便于 Spring Security 后续定位
        String principalName = String.valueOf(localUser.getId());

        return new DefaultOAuth2User(authorities, enrichedAttributes, "id");
    }

    /**
     * 查找或创建本地 sys_user
     * <p>
     * 三段式查找/创建策略（见类注释）。
     * </p>
     */
    private SysUser findOrCreateLocalUser(String provider, String oauthId,
                                          String email, String avatarUrl) {
        // 1. 按 (oauth_provider, oauth_id) 查找
        SysUser existing = sysUserMapper.selectByOAuth(provider, oauthId);
        if (existing != null) {
            log.info("OAuth2 登录命中已绑定账号: provider={}, oauthId={}, userId={}",
                    provider, oauthId, existing.getId());
            // 更新最后登录时间等字段由 SuccessHandler 处理，这里只返回
            return existing;
        }

        // 2. 邮箱相同时绑定到已有账号（用户决策点：邮箱相同时绑定）
        if (StringUtils.hasText(email)) {
            SysUser userByEmail = sysUserMapper.selectByEmail(email);
            if (userByEmail != null) {
                // 绑定第三方账号到已有本地账号
                userByEmail.setOauthProvider(provider);
                userByEmail.setOauthId(oauthId);
                userByEmail.setLoginType(mapLoginType(provider));
                if (StringUtils.hasText(avatarUrl) && !StringUtils.hasText(userByEmail.getAvatar())) {
                    userByEmail.setAvatar(avatarUrl);
                }
                sysUserMapper.updateById(userByEmail);

                log.info("OAuth2 邮箱绑定成功: provider={}, oauthId={}, email={}, userId={}",
                        provider, oauthId, email, userByEmail.getId());
                return userByEmail;
            }
        }

        // 3. 自动创建新 sys_user
        return createNewOAuthUser(provider, oauthId, email, avatarUrl);
    }

    /**
     * 创建新的第三方登录用户
     */
    private SysUser createNewOAuthUser(String provider, String oauthId,
                                       String email, String avatarUrl) {
        // username 由后端统一随机生成，不再暴露第三方平台信息
        String username = usernameGenerator.generate();

        // password 占位：第三方登录不使用密码，但字段不能为空
        String randomPassword = UUID.randomUUID().toString();
        String encodedPassword = passwordEncoder.encode(randomPassword);

        SysUser sysUser = SysUser.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .avatar(avatarUrl)
                .userType(1)
                .status(1)
                .loginType(mapLoginType(provider))
                .oauthProvider(provider)
                .oauthId(oauthId)
                .build();

        sysUserMapper.insert(sysUser);

        // 关联 GUEST 角色
        Long guestRoleId = sysUserRoleMapper.selectRoleIdByCode("GUEST");
        if (guestRoleId == null) {
            throw new BaseException(MessageConstant.GUEST_ROLE_NOT_FOUND);
        }
        SysUserRole userRole = SysUserRole.builder()
                .userId(sysUser.getId())
                .roleId(guestRoleId)
                .build();
        sysUserRoleMapper.insert(userRole);

        log.info("OAuth2 自动注册新用户: provider={}, oauthId={}, userId={}, username={}, email={}",
                provider, oauthId, sysUser.getId(), sysUser.getUsername(), sysUser.getEmail());

        return sysUser;
    }

    /**
     * 平台标识 → login_type 映射
     * <ul>
     *     <li>github → 2</li>
     *     <li>gitee → 3</li>
     *     <li>其他 → 1（本地，兜底）</li>
     * </ul>
     */
    private Integer mapLoginType(String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> 2;
            case "gitee" -> 3;
            default -> 1;
        };
    }
}
