# DriftingLeaves Spring Authorization Server + OAuth2 迁移实施手册

> **目标读者**：参与本次改造的后端/前端开发人员
> **目标**：让拿到这份文档的人，能够按阶段、按步骤独立完成改造
> **总工期预估**：4~6 周
> **核心变化**：从私有 JWT 方案迁移到 Spring Authorization Server + OAuth2 Resource Server

---

## 一、改造前必读

### 1.1 用户诉求（已确认）

| 诉求 | 最终选择 |
|------|---------|
| 安全框架 | **Spring Authorization Server**（授权服务器）+ **Spring OAuth2 Resource Server**（资源服务） |
| 协议规范 | OAuth2.1 + OIDC 1.0 |
| Token 机制 | 标准 JWT **AccessToken** + **RefreshToken** |
| 公钥验证 | 授权服务器维护 **JWKS** 公钥集，资源服务器自动拉取验签 |
| 博客端登录 | **邮箱验证码登录**（登录即注册）+ **GitHub/Gitee OAuth2 登录** |
| 管理员登录 | 用户名密码 + 邮箱验证码双因素认证 |
| 游客账号 | 保留，但仅允许只读访问后台 |
| 历史匿名数据 | **全部清理**，评论/点赞/留言/订阅必须登录后才能产生 |

### 1.2 改造原则

1. **分阶段推进**：每阶段结束必须可独立运行、可测试
2. **先跑通再完善**：先让管理员能登录，再扩展博客端和第三方登录
3. **历史数据备份**：改造前必须全量备份数据库
4. **旧代码不立即删除**：标记为 `@Deprecated`，待稳定后再移除
5. **前端后端同步**：每个涉及接口改造的阶段，前端必须配套改造

### 1.3 你需要先确认的环境

- JDK 21
- Spring Boot 3.5.7（当前项目版本）
- MySQL 8.0+
- Redis（继续用于验证码、限流、缓存）
- Maven

---

## 二、新旧方案对比

### 2.1 架构对比

| 维度 | 旧方案 | 新方案 | 影响 |
|------|--------|--------|------|
| 认证框架 | 自定义 `JwtTokenAdminInterceptor` | Spring Authorization Server + OAuth2 Resource Server | 更标准，学习成本更高 |
| 协议 | 私有 JWT | OAuth2.1 + OIDC 1.0 | 可接入第三方，便于审计 |
| Token | 单一 JWT | AccessToken + RefreshToken | 更安全，支持刷新 |
| 下发方式 | Controller 手动写 Cookie | 授权服务器标准化颁发 | 统一入口，减少手写代码 |
| 校验方式 | 查 Redis 白名单 | JWKS 公钥离线验签 | 性能更好，解耦授权与资源服务 |
| 会话吊销 | 删除 Redis Set | 吊销 RefreshToken / Session | 更标准 |
| 第三方登录 | 无 | GitHub/Gitee | 用户体验提升 |
| 方法鉴权 | 拦截器硬编码 | `@PreAuthorize` | 代码清晰，易扩展 |
| 测试 | 手动构造 Cookie | `@WithMockUser` / OAuth2 Test | 测试效率提升 |

### 2.2 数据库对比

| 表 | 旧方案 | 新方案 |
|----|--------|--------|
| `admin` | 独立表 | **删除**，合并到 `sys_user` |
| `sys_user` | 无 | **新增**，统一用户表 |
| `sys_role` / `sys_user_role` | 无 | **新增**，RBAC |
| `oauth2_registered_client` | 无 | **新增**，OAuth2 客户端 |
| `oauth2_authorization` | 无 | **新增**，授权记录 |
| `oauth2_authorization_consent` | 无 | **新增**，授权同意 |
| `article_comments` | 支持匿名 | 仅 `user_id`，必须登录 |
| `article_likes` | 支持匿名 | 仅 `user_id`，必须登录 |
| `messages` | 支持匿名 | 仅 `user_id`，必须登录 |
| `rss_subscriptions` | 支持匿名 | 仅 `user_id`，必须登录 |
| `operation_logs` | `admin_id` | `user_id` |
| `articles` | `is_published` 0/1 | `status` 0草稿/1待审核/2已发布/3违规，新增 `submit_time` |
| 作者设计 | 无 | 新增 `article_authors` 多对多关联表，支持邀请、编辑权限、角色 |
| 审核记录 | 无 | 新增 `article_audit_logs` 表记录每次状态变更 |

> **完整 SQL 见**：[security-redesign-schema-v2.sql](file:///d:/CodingFiles/Blog/DriftingLeaves-Website/Backend/docs/security-redesign-schema-v2.sql)

### 2.3 登录流程对比

#### 旧方案

```text
前端 POST /admin/admin/login
  -> AdminService 校验用户名/密码/验证码
  -> TokenService 生成 JWT 并存入 Redis Set
  -> AdminController 手动写入 HttpOnly Cookie
  -> JwtTokenAdminInterceptor 每次请求校验 Cookie
```

#### 新方案

```text
前端 POST /oauth2/token
  -> Spring Authorization Server
     -> AdminAuthenticationProvider 校验用户名/密码/验证码
     -> 生成 AccessToken + RefreshToken
     -> 写入 HttpOnly Cookie
  -> 前端后续请求自动携带 Cookie
  -> OAuth2 Resource Server 从 JWKS 拉取公钥验签
     -> 解析 JWT，构建 Authentication
     -> @PreAuthorize 进行方法级鉴权
```

---

## 三、全局实施路线图

```text
阶段一：数据库改造 + 基础实体（1 周）
    │
阶段二：Spring Authorization Server 搭建（1.5~2 周）
    │
阶段三：OAuth2 Resource Server 接入 + 管理员后台迁移（1~1.5 周）
    │
阶段四：博客端登录 + 交互接口改造（1~1.5 周）
    │
阶段五：前端适配 + 测试 + 收尾（1 周）
```

---

## 四、阶段一：数据库改造 + 基础实体

### 4.1 目标

- 完成新库结构搭建
- 完成 MyBatis-Plus 实体、Mapper、Service 生成
- 备份并清理历史匿名数据

### 4.2 执行步骤

#### 步骤 1：备份数据库

```bash
mysqldump -u root -p DriftingLeaves > drifting_leaves_backup_$(date +%Y%m%d).sql
```

#### 步骤 2：执行新 SQL

直接执行 [security-redesign-schema-v2.sql](file:///d:/CodingFiles/Blog/DriftingLeaves-Website/Backend/docs/security-redesign-schema-v2.sql)。

该 SQL 会：
- 删除 `admin` 表
- 创建 `sys_user`、`sys_role`、`sys_user_role`、`sys_permission`、`sys_role_permission`、`sys_login_lock`
- 创建 OAuth2 三张表
- 改造 `operation_logs`、`article_comments`、`article_likes`、`messages`、`rss_subscriptions`
- 清理历史匿名数据

#### 步骤 3：迁移旧管理员数据

如果旧数据库中有管理员账号，执行以下迁移：

```sql
-- 迁移原 admin 表数据到 sys_user
INSERT INTO sys_user (username, password, nickname, email, user_type, status, login_type, create_time, update_time)
SELECT username, password, nickname, email,
       CASE WHEN role = 1 THEN 2 ELSE 3 END AS user_type,
       1 AS status,
       1 AS login_type,
       create_time,
       update_time
FROM admin;

-- 关联角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = CASE
    WHEN u.user_type = 2 THEN 'ADMIN'
    WHEN u.user_type = 3 THEN 'VISITOR'
END;
```

> 注意：旧密码是自定义哈希，需要在后续阶段用双轨验证逐步升级为 BCrypt。

#### 步骤 4：生成 MyBatis-Plus 代码

在 `DL-pojo` 和 `DL-server` 中创建以下实体和 Mapper：

| 实体 | 所在包 |
|------|--------|
| `SysUser` | `com.xuan.entity` |
| `SysRole` | `com.xuan.entity` |
| `SysUserRole` | `com.xuan.entity` |
| `SysPermission` | `com.xuan.entity` |
| `SysRolePermission` | `com.xuan.entity` |
| `SysLoginLock` | `com.xuan.entity` |
| `ArticleComments`（改造） | `com.xuan.entity` |
| `ArticleLikes`（改造） | `com.xuan.entity` |
| `ArticleAuthors`（新增） | `com.xuan.entity` |
| `ArticleAuditLog`（新增） | `com.xuan.entity` |
| `Messages`（改造） | `com.xuan.entity` |
| `RssSubscriptions`（改造） | `com.xuan.entity` |
| `OperationLogs`（改造） | `com.xuan.entity` |

#### 步骤 5：删除旧实体

- 删除 `Admin.java` 实体（或保留到阶段三结束后再删除）

### 4.3 本阶段验收标准

- [ ] 新 SQL 能成功执行
- [ ] 所有新实体和 Mapper 能正常编译
- [ ] 项目能正常启动（此时认证还未接入，可暂时关闭 Security 自动配置）
- [ ] 历史匿名数据已清理

---

## 五、阶段二：Spring Authorization Server 搭建

### 5.1 目标

- 引入 Spring Authorization Server
- 配置授权服务器端点
- 实现管理员登录（用户名 + 密码 + 邮箱验证码）
- 实现 Token 颁发和 Cookie 写入

### 5.2 依赖配置

在 `DL-server/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### 5.3 执行步骤

#### 步骤 1：创建授权服务器配置

新建文件：`DL-server/src/main/java/com/xuan/auth/config/AuthorizationServerConfig.java`

核心配置内容：

```java
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                         RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        // 生成 RSA 密钥对，提供 JWKS 端点
        RSAKey rsaKey = Jwks.generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, context) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer("http://localhost:8080")
            .build();
    }
}
```

#### 步骤 2：初始化 OAuth2 客户端

执行以下 SQL 插入客户端：

```sql
INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris,
    post_logout_redirect_uris, scopes, client_settings, token_settings
) VALUES (
    'admin-client-uuid',
    'admin-client',
    NOW(),
    '{bcrypt}$2a$10$...',  -- 用 BCryptPasswordEncoder 生成
    'Admin Client',
    'client_secret_basic',
    'password,refresh_token',
    '',
    '',
    'openid,profile,admin',
    '{"requireProofKey":false,"requireAuthorizationConsent":false}',
    '{"accessTokenTimeToLive":"30m","refreshTokenTimeToLive":"7d","reuseRefreshTokens":false}'
);
```

#### 步骤 3：实现 UserDetailsService

新建文件：`DL-server/src/main/java/com/xuan/auth/security/UserDetailsServiceImpl.java`

```java
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户不存在或已禁用");
        }

        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());

        return new SecurityUser(user.getId(), user.getUsername(), user.getPassword(),
                                user.getUserType(), user.getNickname(), authorities);
    }
}
```

#### 步骤 4：创建 SecurityUser

新建文件：`DL-server/src/main/java/com/xuan/auth/security/SecurityUser.java`

```java
public class SecurityUser implements UserDetails {
    private Long userId;
    private String username;
    private String password;
    private Integer userType;
    private String nickname;
    private Collection<? extends GrantedAuthority> authorities;
    // 实现 UserDetails 接口方法
}
```

#### 步骤 5：实现管理员双因素认证 Provider

新建文件：`DL-server/src/main/java/com/xuan/auth/security/AdminAuthenticationProvider.java`

```java
@Component
@RequiredArgsConstructor
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String password = (String) authentication.getCredentials();
        String code = authentication.getDetails().toString(); // 验证码

        UserDetails user = userDetailsService.loadUserByUsername(username);
        // 校验密码
        // 校验验证码（管理员才需要）
        // 返回 Authentication
    }
}
```

#### 步骤 6：配置 Spring Security 使用自定义 Provider

```java
@Bean
public AuthenticationManager authenticationManager(
        AdminAuthenticationProvider adminProvider,
        EmailCodeAuthenticationProvider emailCodeProvider,
        DaoAuthenticationProvider daoProvider) {
    return new ProviderManager(adminProvider, emailCodeProvider, daoProvider);
}
```

#### 步骤 7：配置 Token 通过 Cookie 下发

实现 `CustomCookieAuthenticationSuccessHandler`，在登录成功后：

```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                    Authentication authentication) {
    // 从 OAuth2Authorization 中获取 AccessToken 和 RefreshToken
    // 写入 HttpOnly Cookie
}
```

### 5.4 本阶段验收标准

- [ ] `POST /oauth2/token` 能返回 AccessToken 和 RefreshToken
- [ ] 登录成功后 Cookie 中能看到 token
- [ ] `/oauth2/jwks` 能返回公钥集
- [ ] 错误的用户名/密码/验证码返回 401

---

## 六、阶段三：Resource Server 接入 + 管理员后台迁移

### 6.1 目标

- 业务服务接入 OAuth2 Resource Server
- 删除旧的 JWT 拦截器
- 管理员后台接口使用 `@PreAuthorize`
- 游客账号只读权限生效

### 6.2 执行步骤

#### 步骤 1：创建 Resource Server 配置

新建文件：`DL-server/src/main/java/com/xuan/resource/config/ResourceServerConfig.java`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                .accessDeniedHandler(new CustomAccessDeniedHandler())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/admin/login", "/admin/admin/sendCode", "/oauth2/**").permitAll()
                .requestMatchers("/blog/**").permitAll()  // 具体接口用 @PreAuthorize 控制
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
```

#### 步骤 2：配置 JWKS 拉取

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080
```

#### 步骤 3：删除旧认证逻辑

- 删除 `JwtTokenAdminInterceptor.java`
- 在 `WebMvcConfiguration.java` 中移除拦截器注册
- 保留 CORS 配置

#### 步骤 4：改造管理员 Controller

以 `AdminController.java` 为例：

```java
@RestController
@RequestMapping("/admin/admin")
@RequiredArgsConstructor
public class AdminController {

    private final IAdminService adminService;

    // 登录接口由 /oauth2/token 统一处理，此处可删除 login 方法
    // 或保留作为兼容入口，内部转发到 /oauth2/token

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VISITOR')")
    public Result<AdminVO> getAdminInfo(@AuthenticationPrincipal SecurityUser user) {
        return Result.success(adminService.getAdminById(user.getUserId()));
    }
}
```

#### 步骤 5：游客只读权限

```java
@PreAuthorize("hasRole('ADMIN') or (hasRole('VISITOR') and #request.method == 'GET')")
public Result<?> someAdminEndpoint(HttpServletRequest request) {
    // ...
}
```

#### 步骤 6：改造操作日志

- `OperationLogAspect.java` 中 `admin_id` 改为从 `SecurityContextHolder` 取 `user_id`
- `OperationLogs` 实体 `adminId` 字段改为 `userId`

### 6.3 本阶段验收标准

- [ ] 旧拦截器已删除
- [ ] 未登录访问 `/admin/**` 返回 401
- [ ] 游客访问非 GET 后台接口返回 403
- [ ] 管理员能正常访问所有后台接口
- [ ] 操作日志记录正确的 user_id

---

## 七、阶段四：博客端登录 + 交互接口改造

### 7.1 目标

- 实现博客端邮箱验证码登录
- 实现 GitHub/Gitee OAuth2 登录
- 评论/点赞/留言/订阅接口要求 `ROLE_USER`
- 清理历史匿名数据

### 7.2 执行步骤

#### 步骤 1：邮箱验证码登录 Provider

新建 `EmailCodeAuthenticationProvider.java`：

```java
@Component
@RequiredArgsConstructor
public class EmailCodeAuthenticationProvider implements AuthenticationProvider {

    private final SysUserMapper sysUserMapper;
    private final VerifyCodeService verifyCodeService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String email = authentication.getName();
        String code = (String) authentication.getCredentials();

        // 校验验证码
        if (!verifyCodeService.verifyEmailCode(email, code)) {
            throw new BadCredentialsException("验证码错误");
        }

        // 查找或创建用户
        SysUser user = sysUserMapper.selectByUsername(email);
        if (user == null) {
            user = registerBlogUser(email);
        }

        // 加载角色并返回 Authentication
    }
}
```

#### 步骤 2：发送邮箱验证码接口

```java
@PostMapping("/auth/email-code")
public Result sendEmailCode(@RequestBody @Valid SendEmailCodeDTO dto) {
    verifyCodeService.sendEmailCode(dto.getEmail());
    return Result.success();
}
```

#### 步骤 3：GitHub/Gitee OAuth2 登录

配置 `ClientRegistrationRepository`：

```java
@Bean
public ClientRegistrationRepository clientRegistrationRepository() {
    return new InMemoryClientRegistrationRepository(
        githubClientRegistration(),
        giteeClientRegistration()
    );
}
```

实现 `OAuth2UserService`：

```java
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String oauthId = oAuth2User.getAttribute("id").toString();
        String email = oAuth2User.getAttribute("email");
        String nickname = oAuth2User.getAttribute("name");
        String avatar = oAuth2User.getAttribute("avatar_url");

        // 查找或创建 sys_user
        SysUser user = sysUserMapper.selectByOAuth(provider, oauthId);
        if (user == null) {
            user = registerOAuthUser(provider, oauthId, email, nickname, avatar);
        }

        // 返回带角色的 OAuth2User
    }
}
```

#### 步骤 4：改造博客交互接口

```java
@RestController
@RequestMapping("/blog/comment")
@RequiredArgsConstructor
public class ArticleCommentController {

    private final IArticleCommentService commentService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Result addComment(@RequestBody @Valid ArticleCommentDTO dto,
                             @AuthenticationPrincipal SecurityUser user) {
        dto.setUserId(user.getUserId());
        commentService.addComment(dto);
        return Result.success();
    }
}
```

同理改造：
- `ArticleLikeController`
- `MessageController`
- `RssSubscriptionController`

#### 步骤 5：清理历史匿名数据

执行 SQL：

```sql
TRUNCATE TABLE article_comments;
TRUNCATE TABLE article_likes;
TRUNCATE TABLE messages;
TRUNCATE TABLE rss_subscriptions;
UPDATE articles SET comment_count = 0, like_count = 0;
```

### 7.3 本阶段验收标准

- [ ] 博客端邮箱验证码能登录
- [ ] 首次邮箱验证码登录自动注册
- [ ] GitHub/Gitee 能登录并绑定用户
- [ ] 未登录用户调用评论接口返回 401
- [ ] 已登录用户能正常评论/点赞/留言/订阅
- [ ] 历史匿名数据已清空

---

## 八、阶段五：前端适配 + 测试 + 收尾

### 8.1 前端改造清单

#### 8.1.1 新增页面

- `Frontend-Blog/src/views/Login/index.vue`
  - 邮箱验证码输入框
  - 发送验证码按钮
  - GitHub/Gitee 登录按钮
- `Frontend-Blog/src/views/Profile/index.vue`（可选）

#### 8.1.2 改造请求层

在 `Frontend-Blog/src/utils/request.js` 中：

```javascript
import axios from 'axios'
import router from '@/router'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true
})

http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      try {
        await axios.post('/oauth2/token', {
          grant_type: 'refresh_token',
          client_id: 'blog-client',
          refresh_token: getCookie('refresh_token')
        }, { withCredentials: true })
        return http(originalRequest)
      } catch (e) {
        router.push('/login')
      }
    }

    if (error.response?.status === 403) {
      ElMessage.error('权限不足')
    }

    return Promise.reject(error)
  }
)
```

#### 8.1.3 改造交互组件

| 组件 | 未登录状态 | 已登录状态 |
|------|-----------|-----------|
| 评论输入框 | 显示"登录后评论"按钮，点击跳转 `/login` | 显示当前用户头像，可输入评论 |
| 点赞按钮 | 点击弹出登录提示 | 可点赞/取消点赞 |
| 留言输入框 | 显示"登录后留言" | 可输入留言 |
| RSS 订阅 | 显示"登录后订阅" | 一键订阅 |

#### 8.1.4 顶部导航

- 未登录：显示"登录"按钮
- 已登录：显示用户头像昵称，点击可退出登录

### 8.2 测试清单

#### 8.2.1 后端测试

- 单元测试：
  - `AdminAuthenticationProviderTest`
  - `EmailCodeAuthenticationProviderTest`
  - `CustomOAuth2UserServiceTest`
- 接口测试：
  - `@WithMockUser(roles = "ADMIN")`
  - `@WithMockUser(roles = "USER")`
  - 未登录访问受保护接口返回 401

#### 8.2.2 前端测试

- 未登录点击评论/点赞/留言/订阅是否正常跳转登录
- 登录后 Token 是否正确写入 Cookie
- 401 自动刷新是否成功
- 刷新失败是否正确跳转登录

### 8.3 收尾工作

1. 删除以下旧文件：
   - `JwtTokenAdminInterceptor.java`
   - `TokenService.java`
   - `TokenServiceImpl.java`
   - `Admin.java` 实体
   - `AdminMapper.java`
   - 旧的 `EncryptPasswordServiceImpl.java`（迁移完成后）

2. 更新 `application.yml` 中的安全配置
3. 更新部署文档
4. 更新 CONTRIBUTING.md 中的本地启动说明

### 8.4 本阶段验收标准

- [ ] 前端登录页可用
- [ ] 所有交互组件已适配登录态
- [ ] 401/403 处理正确
- [ ] 单元测试覆盖核心认证逻辑
- [ ] 旧代码已清理
- [ ] 文档已更新

---

## 九、关键业务规则（新增：文章多作者 + 审核）

### 9.1 文章状态流转

```text
草稿 <-------------------> 待审核 <-------------------> 已发布
  ^    （作者/管理员撤回）   |   （管理员审核通过）            |
  |                        |                               |
  |                        └-------------------> 违规     |
  |                            （管理员标记违规）           |
  └---------------------------------------------------------
                            （管理员撤销违规后回到草稿/待审核）
```

| 状态 | 值 | 可见性 | 可编辑 |
|------|---|--------|--------|
| 草稿 | 0 | 作者本人 + 管理员 | 是 |
| 待审核 | 1 | 作者本人 + 管理员 | 第一作者可撤回为草稿 |
| 已发布 | 2 | 所有人 | 是（修改后建议重新进入待审核，可选） |
| 违规 | 3 | 作者本人 + 管理员 | 否（需管理员撤销违规） |

### 9.2 作者权限规则

| 角色 | 权限 |
|------|------|
| 第一作者 | 编辑文章、邀请/移除共同作者、提交审核、撤回草稿、删除文章 |
| 共同作者（can_edit=1） | 编辑文章内容 |
| 共同作者（can_edit=0） | 仅展示署名，不可编辑 |
| 管理员 | 审核文章、标记违规、设置置顶、删除任何文章 |

### 9.3 邀请流程

```text
第一作者 -> 输入对方用户名/邮箱 -> 发送邀请
  -> 在 article_authors 中插入记录：invite_status=0, can_edit=默认1
  -> 被邀请人收到通知（可选）
  -> 被邀请人接受：invite_status=1
  -> 被邀请人拒绝：invite_status=2
  -> 第一作者移除：invite_status=3
```

### 9.4 文章置顶规则

- 只有管理员可以设置 `is_top = 1`
- 置顶文章必须是 `status = 2`（已发布）
- 前端展示时优先按 `is_top DESC, publish_time DESC` 排序

### 9.5 审核记录

每次状态变更必须写入 `article_audit_logs`：

```java
articleAuditLogService.record(
    articleId,
    currentUserId,
    fromStatus,
    toStatus,
    "审核通过：内容符合规范"
);
```

### 9.6 关键接口权限示例

```java
// 发布文章（创建草稿）
@PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public Result createArticle(@RequestBody @Valid ArticleDTO dto,
                            @AuthenticationPrincipal SecurityUser user) {
    Long articleId = articleService.createDraft(dto, user.getUserId());
    return Result.success(articleId);
}

// 提交审核
@PostMapping("/{id}/submit")
@PreAuthorize("@articlePermissionService.isFirstAuthor(#id, authentication.name)")
public Result submitForReview(@PathVariable Long id) {
    articleService.submitForReview(id);
    return Result.success();
}

// 管理员审核通过
@PostMapping("/admin/{id}/approve")
@PreAuthorize("hasRole('ADMIN')")
public Result approve(@PathVariable Long id, @RequestParam String reason) {
    articleService.approve(id, reason);
    return Result.success();
}

// 设置置顶
@PostMapping("/admin/{id}/top")
@PreAuthorize("hasRole('ADMIN')")
public Result setTop(@PathVariable Long id, @RequestParam Boolean top) {
    articleService.setTop(id, top);
    return Result.success();
}
```

---

## 十、关键代码示例

### 10.1 生成 RSA 密钥对（JWKS）

```java
public final class Jwks {

    private Jwks() {}

    public static RSAKey generateRsa() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString())
            .build();
    }

    private static KeyPair generateRsaKey() {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }
}
```

### 10.2 Token 写入 Cookie

```java
public static void addTokenCookie(HttpServletResponse response, String tokenName,
                                  String token, long maxAgeSeconds, boolean secure) {
    String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
    String secureFlag = secure ? "; Secure" : "";
    String cookieValue = String.format(
        "%s=%s; Max-Age=%d; Path=/; HttpOnly%s; SameSite=Strict",
        tokenName, encodedToken, maxAgeSeconds, secureFlag
    );
    response.addHeader("Set-Cookie", cookieValue);
}
```

### 10.3 从 SecurityContext 取当前用户

```java
public static SecurityUser getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
        return null;
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof SecurityUser) {
        return (SecurityUser) principal;
    }
    return null;
}
```

---

## 十一、风险与回滚方案

| 风险 | 应对措施 | 回滚方案 |
|------|---------|---------|
| Spring Authorization Server 配置复杂，第一阶段跑不通 | 先用最小配置（一个客户端、一种 grant_type）跑通 | 回退到阶段一之前，恢复 `JwtTokenAdminInterceptor` |
| 历史数据清理后无法恢复 | 改造前全量备份 | 从备份恢复 |
| 前端 Token 刷新并发问题 | 使用请求队列，避免重复刷新 | 简化处理：401 直接跳转登录 |
| 第三方登录回调配置错误 | 先在本地用 GitHub OAuth App 测试 | 关闭第三方登录入口 |
| 旧密码双轨验证有 bug | 增加日志，逐步验证 | 回退密码验证逻辑到旧实现 |
| OAuth2 password grant 安全争议 | 管理端内网使用；博客端使用授权码 + PKCE | 博客端也改为授权码模式 |

---

## 十二、给实施者的直接指引

### 12.1 每天开始工作前

1. 确认当前处于哪个阶段
2. 查看本阶段的验收标准
3. 每次只做一个步骤，完成后自测

### 12.2 遇到问题时

1. 先检查 `/oauth2/jwks` 是否能返回公钥
2. 再检查 `oauth2_registered_client` 中客户端配置是否正确
3. 然后检查 Resource Server 的 `issuer-uri` 是否配置正确
4. 最后查看 `oauth2_authorization` 表中是否有授权记录

### 12.3 代码提交建议

每个阶段结束后提交一次，commit message 示例：

```text
feat(security): 阶段一完成数据库改造和实体生成

- 删除 admin 表，新增 sys_user/sys_role 等表
- 清理历史匿名评论/点赞/留言/订阅数据
- 生成 MyBatis-Plus 实体和 Mapper
```

---

## 十三、总结

本次改造分为 **5 个阶段**，从数据库到授权服务器，再到资源服务器、博客端登录、前端适配，逐步将项目从私有 JWT 方案迁移到标准 OAuth2.1/OIDC 体系。

核心文件位置：
- 数据库 SQL：[security-redesign-schema-v2.sql](file:///d:/CodingFiles/Blog/DriftingLeaves-Website/Backend/docs/security-redesign-schema-v2.sql)
- 架构设计文档：本文件

建议按阶段顺序执行，每阶段验收通过后再进入下一阶段。
