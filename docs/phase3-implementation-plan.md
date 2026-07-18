# DriftingLeaves 阶段三分步实施计划书

> **文档定位**：阶段三（OAuth2 Resource Server 接入 + 管理员后台迁移）的可执行分步计划书
> **配套设计文档**：
> - [phase3-rbac-design.md](./phase3-rbac-design.md) —— 4 角色 RBAC 详细设计
> - [spring-security-oauth2-migration-plan.md](./spring-security-oauth2-migration-plan.md) —— 整体迁移路线
> **核心目标**：完成 4 角色 RBAC 权限落地、Token Cookie 下发、旧代码清理与全量验收
> **前置条件**：阶段二已完成（最小授权服务器跑通 + Resource Server 最小接入 + JWT 含 `roles` claim）

---

## 一、文档使用说明

1. 本计划书将阶段三拆分为 **8 个步骤**，每步可独立编译、启动、自测。
2. 每个步骤包含：目标、依赖、改动文件、关键代码、验收点。
3. **必须按顺序执行**，后置步骤依赖前置步骤的产物。
4. 每完成一步执行 `mvn clean package -pl DL-server -am` 验证编译，并启动服务做接口冒烟测试。

---

## 二、角色设计回顾（重点）

### 2.1 4 个核心角色

| 角色编码 | 角色名称 | 原 user_type | 定位 | 可访问区域 |
|---------|---------|-------------|------|-----------|
| `ADMIN` | 超级管理员 | 2 | 系统最高权限，包含审核/置顶/监控等专属能力 | `/admin/**` 全部 + `/blog/**` 全部 |
| `AUTHOR` | 内容创作者 | 4（新增） | 仅管理自己的文章全生命周期及自己文章下的评论 | `/admin/article/**` 部分 + `/admin/article/comment/**` 部分 |
| `AUDITOR` | 后台审计员 | 3 | 后台只读审计，可查看所有数据但不可修改 | `/admin/**` 所有 GET（服务器监控除外） |
| `GUEST` | 普通访客 | 1 | 前台交互用户，与后台完全隔离 | `/blog/**` 交互接口 |

### 2.2 角色正交原则

- **不互相包含**：`ADMIN` 不隐含 `AUTHOR`，管理员要发文章必须额外关联 `AUTHOR` 角色
- **职责不重叠**：每个角色负责独立功能域
- **叠加生效**：通过 `sys_user_role` 多对多关联，多角色权限取并集
- **最小权限**：默认拒绝，仅显式授权的接口可访问

### 2.3 多角色组合场景

| 账号类型 | 角色组合 | 适用场景 |
|---------|---------|---------|
| 纯管理员 | `ADMIN` | 运维人员 |
| 站长（全能型） | `ADMIN` + `AUTHOR` + `GUEST` | 个人博客站长 |
| 投稿作者 | `AUTHOR` + `GUEST` | 特邀嘉宾 |
| 内容合伙人 | `AUTHOR` + `AUDITOR` | 核心团队成员 |
| 纯审计员 | `AUDITOR` | 外部审计人员 |
| 纯访客 | `GUEST` | 普通注册用户 |

### 2.4 后台接口权限矩阵（核心摘要）

> 完整矩阵见 [phase3-rbac-design.md 第四节](./phase3-rbac-design.md#四后台接口权限矩阵admin)

| 模块 | ADMIN | AUTHOR | AUDITOR | GUEST | 未登录 |
|------|:-----:|:------:|:-------:|:-----:|:------:|
| `/admin/admin/login`（POST） | ✅ | ✅ | ✅ | ✅ | ✅(permitAll) |
| `/admin/article/**` GET | ✅ | ✅(仅自己) | ✅ | ❌ | 401 |
| `/admin/article` POST/PUT/DELETE | ✅ | ✅(仅自己) | ❌ | ❌ | 401 |
| `/admin/article/top/{id}` PUT | ✅ | ❌ | ❌ | ❌ | 401 |
| `/admin/articleCategory` GET | ✅ | ✅ | ✅ | ❌ | 401 |
| `/admin/articleCategory` POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | 401 |
| `/admin/article/comment/**` GET | ✅ | ✅(仅自己文章) | ✅ | ❌ | 401 |
| `/admin/article/comment/**` 写操作 | ✅ | ✅(仅自己文章) | ❌ | ❌ | 401 |
| 留言/友链/音乐/经历/技能/社交/系统配置 GET | ✅ | ❌ | ✅ | ❌ | 401 |
| 留言/友链/音乐等模块写操作 | ✅ | ❌ | ❌ | ❌ | 401 |
| `/admin/visitor`、`/admin/view`、`/admin/operationLog` GET | ✅ | ❌ | ✅ | ❌ | 401 |
| `/admin/report/**` GET | ✅ | ❌ | ✅ | ❌ | 401 |
| `/admin/server-monitor/**` | ✅ | ❌ | ❌ | ❌ | 401 |
| `/admin/common/upload` POST | ✅ | ✅ | ❌ | ❌ | 401 |

### 2.5 三层权限控制架构

```text
第一层：URL 级粗粒度（ResourceServerConfig）
  └─ HTTP 方法 + URL 模式匹配，快速拒绝非法请求
        ↓
第二层：类级 @PreAuthorize（Controller 类注解）
  └─ 控制整个模块的访问角色，排除不相关角色
        ↓
第三层：方法级 @PreAuthorize（Controller 方法注解）
  └─ 精细控制 + SpEL 调用 ArticlePermissionService 做数据范围校验
```

---

## 三、Spring Security 引入后的新业务流程说明

> **本节目的**：明确引入 Spring Authorization Server + OAuth2 Resource Server 后，各核心业务流程的完整链路。开发者在动手编码前必须理解这些流程，才能正确实现步骤 1-8。

### 3.1 新旧架构对比总览

| 维度 | 旧方案（私有 JWT） | 新方案（Spring Security） |
|------|------------------|--------------------------|
| 登录入口 | `POST /admin/admin/login`（Controller 处理） | `POST /oauth2/token`（SAS 端点处理） |
| 认证 Provider | `AdminService.verify()` | `AdminPasswordCodeAuthenticationProvider` |
| Token 生成 | `TokenService.generate()` 手写 JWT | SAS 内置 `JwtGenerator` 自动生成 |
| Token 下发 | Controller 手动写 Cookie | `OAuth2TokenResponseCookieHandler` 拦截响应 |
| Token 校验 | `JwtTokenAdminInterceptor` 拦截 + Redis 白名单 | Resource Server JWKS 离线验签 |
| 角色加载 | `AdminService` 单一查询 | `UserDetailsServiceImpl` + `sys_user_role` 多对多 |
| 权限判断 | 拦截器硬编码 URL → user_type | 三层架构：URL → 类级 → 方法级 `@PreAuthorize` |
| 用户身份传递 | `BaseContext`（ThreadLocal） | `SecurityContextHolder` + `@AuthenticationPrincipal` |
| 操作日志 user_id | `BaseContext.getCurrentId()` | `SecurityContextHolder` 取 `SecurityUser.userId` |

### 3.2 管理员登录流程（新）

```text
┌──────────────────────────────────────────────────────────────────┐
│ 1. 前端发起登录请求                                                │
│    POST /oauth2/token                                            │
│    Header: Authorization: Basic admin-client:admin-client-secret │
│    Body: grant_type=admin_password_code                          │
│          username=admin&password=xxx&code=123456                 │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 2. Spring Authorization Server 接收请求                            │
│    └─ OAuth2AuthorizationServerConfigurer 匹配 /oauth2/**         │
│    └─ SecurityFilterChain @Order(1) 优先处理                       │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 3. AdminPasswordCodeAuthenticationConverter 解析参数               │
│    └─ 从 request 读取 username/password/code                    │
│    └─ 构造未认证的 AdminPasswordCodeAuthenticationToken           │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 4. AdminPasswordCodeAuthenticationProvider 认证                    │
│    └─ userDetailsService.loadUserByUsername(admin)              │
│       └─ SysUserMapper.selectByUsername                         │
│       └─ SysUserRoleMapper.selectRoleCodesByUserId               │
│       └─ 返回 SecurityUser（含 ROLE_ADMIN/ROLE_AUTHOR/ROLE_GUEST）│
│    └─ passwordEncoder.matches(password, user.password)           │
│    └─ verifyCodeService.verifyCode(userId, code)                 │
│    └─ 认证通过，返回已认证的 Token                                 │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 5. JwtCustomizerConfig 写入 roles claim                            │
│    └─ OAuth2TokenCustomizer 回调                                  │
│    └─ context.getPrincipal().getAuthorities() → roles           │
│    └─ JWT payload: {"sub":"admin","roles":["ROLE_ADMIN",...]}   │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 6. SAS 生成 AccessToken + RefreshToken                            │
│    └─ 存入 oauth2_authorization 表（JdbcOAuth2AuthorizationService）│
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 7. OAuth2TokenResponseCookieHandler 拦截成功响应                    │
│    └─ 写入 HttpOnly Cookie: access_token、refresh_token          │
│    └─ 同时返回 JSON 响应体（兼容 Header 模式）                    │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 8. 前端接收响应                                                    │
│    └─ Cookie 自动存储（浏览器行为）                                │
│    └─ 后续请求浏览器自动携带 Cookie                                │
│    └─ 可选：从响应体取 access_token 存入内存用于 Header 模式       │
└──────────────────────────────────────────────────────────────────┘
```

### 3.3 后台请求认证与鉴权流程（新）

以 `GET /admin/article/page` 为例，展示一个完整的请求从到达到业务逻辑执行的全链路：

```text
┌──────────────────────────────────────────────────────────────────┐
│ 1. 请求到达                                                       │
│    GET /admin/article/page                                       │
│    Cookie: access_token=eyJraWQ...                              │
│    （或 Header: Authorization: Bearer eyJraWQ...）                │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 2. SecurityFilterChain 选择                                       │
│    └─ /oauth2/** → @Order(1) 授权服务器链（不匹配）              │
│    └─ /admin/**  → @Order(2) Resource Server 链（命中）         │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 3. CookieBearerTokenResolver 解析 Token                            │
│    └─ 优先：Authorization Header → Bearer Token                 │
│    └─ 其次：Cookie access_token → URLDecoder 解码                │
│    └─ 都没有 → 返回 null（后续触发 401）                         │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 4. JwtAuthenticationProvider 验证 Token                           │
│    └─ 从 issuer-uri 拉取 JWKS 公钥（首次拉取后缓存）             │
│    └─ RSA 验签 + 校验 exp/nbf/iss                               │
│    └─ 验证失败 → CustomAuthenticationEntryPoint 返回 401 JSON   │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 5. JwtAuthenticationConverter 转换身份                            │
│    └─ 从 JWT roles claim 读取 ["ROLE_ADMIN","ROLE_AUTHOR",...]   │
│    └─ 构造 JwtAuthenticationToken                               │
│    └─ principal = JWT.sub（用户名）                              │
│    └─ authorities = [ROLE_ADMIN, ROLE_AUTHOR, ...]               │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 6. SecurityContextToBaseContextInterceptor 同步上下文             │
│    └─ 从 SecurityContextHolder 取 Authentication                │
│    └─ 查 sys_user 表补全 SecurityUser（id/nickname/userType）    │
│    └─ 写入 BaseContext（兼容旧代码 ThreadLocal 取用户）          │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 7. 第一层：URL 粗粒度权限（authorizeHttpRequests）                │
│    └─ GET /admin/** → hasAnyRole(ADMIN, AUTHOR, AUDITOR)        │
│    └─ GUEST 被拒绝 → CustomAccessDeniedHandler 返回 403 JSON    │
│    └─ 未认证 → 401                                               │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 8. 第二层：类级 @PreAuthorize                                     │
│    └─ ArticleController 无类级注解 → 跳过此层                    │
│    └─ （若访问 MessageController，类级排除 AUTHOR）              │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 9. 第三层：方法级 @PreAuthorize                                   │
│    └─ @PreAuthorize("hasAnyRole('ADMIN','AUTHOR','AUDITOR')")   │
│    └─ 通过 → 进入业务方法                                        │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 10. Service 层执行业务                                            │
│     └─ ArticleService.pageQuery(dto)                            │
│     └─ AUTHOR 角色：仅返回当前用户作为作者的文章（数据范围过滤）   │
│     └─ ADMIN/AUDITOR 角色：返回所有文章                          │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 11. OperationLogAspect 记录操作日志                                │
│     └─ 从 SecurityContextHolder 取 SecurityUser.userId           │
│     └─ 写入 operation_logs.user_id                               │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 12. 返回 JSON 响应                                                │
│     └─ Result<PageResult<ArticleVO>>                            │
└──────────────────────────────────────────────────────────────────┘
```

### 3.4 AUTHOR 编辑文章流程（数据范围校验）

以 `PUT /admin/article`（AUTHOR 编辑自己的文章）为例，展示 `ArticlePermissionService` 的调用链路：

```text
前端：PUT /admin/article  Body: {id: 100, title: "xxx", content: "yyy"}
  ↓
SecurityFilterChain @Order(2) 接管
  ↓
CookieBearerTokenResolver 解析 Token（含 ROLE_AUTHOR）
  ↓
JWT 验签通过，构造 Authentication
  ↓
第一层 URL：PUT /admin/** → hasAnyRole(ADMIN, AUTHOR) → 通过
  ↓
第二层类级：ArticleController 无类级注解 → 通过
  ↓
第三层方法级：
  @PreAuthorize("hasRole('ADMIN') or 
                 (hasRole('AUTHOR') and 
                  @articlePermissionService.isAuthor(#articleDTO.id, authentication.name))")
  
  ┌─ SpEL 求值流程 ─────────────────────────────────────────┐
  │ 1. hasRole('ADMIN') → false（当前是 AUTHOR）            │
  │ 2. hasRole('AUTHOR') → true                              │
  │ 3. @articlePermissionService.isAuthor(100, "author")     │
  │    └─ sysUserMapper.selectByUsername("author") → userId=5│
  │    └─ articleAuthorsMapper.existsByArticleIdAndUserId(   │
  │         articleId=100, userId=5)                          │
  │       └─ SQL: SELECT COUNT(1) FROM article_authors      │
  │                WHERE article_id=100 AND user_id=5        │
  │                  AND invite_status=1                     │
  │       └─ 返回 true（用户是文章作者）                      │
  │ 4. SpEL 结果：false or (true and true) = true → 放行     │
  └──────────────────────────────────────────────────────────┘
  ↓
ArticleController.updateArticle(articleDTO) 执行
  ↓
ArticleService.updateArticle() 修改数据库
  ↓
返回 200
```

**如果 AUTHOR 试图编辑他人文章**：

```text
SpEL: @articlePermissionService.isAuthor(100, "author")
  └─ SQL 查询返回 0 条 → existsByArticleIdAndUserId 返回 false
  └─ SpEL: false or (true and false) = false
  └─ AccessDeniedException 抛出
  └─ CustomAccessDeniedHandler 拦截 → 返回 403 JSON
```

### 3.5 Token 刷新流程（新）

```text
┌──────────────────────────────────────────────────────────────────┐
│ 触发场景：access_token 过期（30 分钟）                            │
│ 前端请求 → Resource Server → 401 Token Expired                   │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 前端拦截器捕获 401                                                 │
│    └─ axios.interceptors.response.use(error => ...)              │
│    └─ 判断 error.response.status === 401                         │
│    └─ 判断 !originalRequest._retry（避免循环）                   │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 前端发起刷新请求                                                   │
│    POST /oauth2/token                                            │
│    Body: grant_type=refresh_token                                │
│          refresh_token=xxx                                      │
│          client_id=admin-client                                  │
│    Cookie: refresh_token=xxx（自动携带）                         │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ SAS 处理 refresh_token grant                                      │
│    └─ JdbcOAuth2AuthorizationService.findByToken(refreshToken)   │
│    └─ 校验 refresh_token 是否存在、未过期、未被吊销               │
│    └─ 生成新的 access_token + 新的 refresh_token                  │
│    └─ 旧 refresh_token 失效（reuseRefreshTokens=false）          │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ OAuth2TokenResponseCookieHandler 写入新 Cookie                     │
│    └─ 覆盖旧 access_token Cookie                                  │
│    └─ 覆盖旧 refresh_token Cookie                                 │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 前端重试原请求                                                    │
│    └─ originalRequest._retry = true（标记已重试）                 │
│    └─ axios(originalRequest) 重新发起                             │
│    └─ 浏览器自动携带新 access_token Cookie                        │
│    └─ 200 OK                                                     │
└──────────────────────────────────────────────────────────────────┘
```

> ⚠️ 若 refresh_token 也已过期（7 天未活动），刷新接口返回 401，前端跳转登录页。

### 3.6 退出登录流程（新）

```text
┌──────────────────────────────────────────────────────────────────┐
│ 1. 前端调用退出接口                                                │
│    POST /admin/admin/logout                                      │
│    Cookie: access_token=xxx; refresh_token=yyy                   │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 2. AdminController.logout() 处理                                  │
│    └─ 从 SecurityContextHolder 取当前 Authentication             │
│    └─ 调用 OAuth2AuthorizationService.remove(authorization)      │
│       └─ 从 oauth2_authorization 表删除该用户的授权记录           │
│    └─ 清除 Cookie：                                               │
│       Set-Cookie: access_token=; Max-Age=0; Path=/              │
│       Set-Cookie: refresh_token=; Max-Age=0; Path=/             │
└──────────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────┐
│ 3. 返回 200                                                       │
│    └─ 前端跳转登录页                                              │
└──────────────────────────────────────────────────────────────────┘
```

> 说明：由于 Token 是无状态 JWT，服务端无法"撤销"未过期的 access_token。退出登录的主要作用是：
> 1. 删除服务端的 refresh_token，阻止后续刷新（30 分钟后 access_token 自然失效）
> 2. 清除浏览器 Cookie，前端立即停止携带 Token

### 3.7 操作日志记录流程（改造后）

```text
业务方法执行（被 @OperationLog 注解标记）
  ↓
OperationLogAspect 环绕通知触发
  ↓
┌─ 获取当前用户 ─────────────────────────────────────────┐
│ 旧方案：Long userId = BaseContext.getCurrentId();      │
│ 新方案：Authentication auth = SecurityContextHolder     │
│           .getContext().getAuthentication();           │
│        if (auth.getPrincipal() instanceof SecurityUser)│
│            Long userId = ((SecurityUser)              │
│                auth.getPrincipal()).getUserId();       │
│        else                                            │
│            Long userId = null; // 未登录或匿名         │
└────────────────────────────────────────────────────────┘
  ↓
记录操作类型、方法名、参数、耗时
  ↓
INSERT INTO operation_logs (user_id, operation, method, params, time, ip)
VALUES (userId, 'UPDATE', 'ArticleController.updateArticle', '{...}', 45, '127.0.0.1')
```

### 3.8 文章发布完整流程（AUTHOR 视角）

展示一个 AUTHOR 从登录到发布文章的完整业务流程：

```text
1. 登录获取 Token
   POST /oauth2/token (admin_password_code grant)
   → 返回 access_token + refresh_token（Cookie + JSON）
   → JWT roles: ["ROLE_AUTHOR", "ROLE_GUEST"]

2. 创建草稿
   POST /admin/article
   Body: {title: "我的文章", content: "...", categoryId: 1, tagIds: [1,2]}
   → @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')") 通过
   → ArticleService.createDraft(dto, userId)
     └─ INSERT INTO articles (..., status=0 草稿, ...)
     └─ INSERT INTO article_authors (article_id, user_id, author_role=1, invite_status=1)
     └─ 返回 articleId

3. 编辑文章
   PUT /admin/article
   Body: {id: 100, title: "修改后标题", content: "..."}
   → @PreAuthorize("hasRole('ADMIN') or 
                    (hasRole('AUTHOR') and 
                     @articlePermissionService.isAuthor(100, 'author'))")
   → ArticlePermissionService.isAuthor(100, "author")
     └─ 查 article_authors 表，确认用户是文章作者
   → ArticleService.updateArticle(dto)

4. 提交审核（状态流转：草稿 → 待审核）
   PUT /admin/article/status/100?status=1
   → @PreAuthorize("hasRole('ADMIN') or 
                    (hasRole('AUTHOR') and 
                     @articlePermissionService.isAuthor(100, 'author'))")
   → ArticleService.updateStatus(100, 1)
     └─ UPDATE articles SET status=1, submit_time=NOW() WHERE id=100
     └─ INSERT INTO article_audit_logs (article_id, from_status=0, to_status=1, ...)

5. 管理员审核（状态流转：待审核 → 已发布）
   PUT /admin/article/audit/100?status=2
   → @PreAuthorize("hasRole('ADMIN')")  // 仅 ADMIN 可审核
   → ArticleService.updateStatus(100, 2)
     └─ UPDATE articles SET status=2, publish_time=NOW() WHERE id=100
     └─ INSERT INTO article_audit_logs (article_id, from_status=1, to_status=2, ...)

6. AUTHOR 尝试置顶（应失败）
   PUT /admin/article/top/100?isTop=1
   → @PreAuthorize("hasRole('ADMIN')")  // 仅 ADMIN 可置顶
   → 403 Forbidden

7. AUTHOR 查看自己的文章列表
   GET /admin/article/page
   → @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')") 通过
   → ArticleService.pageQuery(dto)
     └─ AUTHOR 角色：SQL 加 WHERE article_id IN (
         SELECT article_id FROM article_authors WHERE user_id = #{currentUserId}
       )
     └─ AUDITOR/ADMIN 角色：无过滤，返回全部

8. AUTHOR 管理自己文章下的评论
   GET /admin/article/comment/100
   → @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')") 通过
   → ArticleCommentService.getByArticleId(100)
     └─ AUTHOR 角色：校验文章 100 是否属于当前用户
     └─ AUDITOR/ADMIN：无校验，返回评论列表

   PUT /admin/article/comment/approve?ids=[1,2,3]
   → @PreAuthorize("hasRole('ADMIN') or 
                    (hasRole('AUTHOR') and 
                     @articlePermissionService.areCommentsInOwnArticle([1,2,3], 'author'))")
   → 逐个校验评论 1,2,3 所在的文章是否都是当前 AUTHOR 的
   → 全部通过 → 批量审核通过
```

### 3.9 多角色叠加生效流程

以 `admin` 账号（同时拥有 ADMIN + AUTHOR + GUEST）访问接口为例：

```text
JWT payload:
{
  "sub": "admin",
  "roles": ["ROLE_ADMIN", "ROLE_AUTHOR", "ROLE_GUEST"],
  "scope": ["openid", "profile", "admin", "author", "auditor"]
}
  ↓
JwtAuthenticationConverter 解析：
  authorities = [
    SimpleGrantedAuthority("ROLE_ADMIN"),
    SimpleGrantedAuthority("ROLE_AUTHOR"),
    SimpleGrantedAuthority("ROLE_GUEST")
  ]
  ↓
访问 /admin/article（POST，创建文章）：
  方法级：@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
  └─ hasRole('ADMIN') = true → 直接放行（无需校验 isAuthor）
  
访问 /admin/article（PUT，编辑文章 id=100）：
  方法级：@PreAuthorize("hasRole('ADMIN') or 
                         (hasRole('AUTHOR') and @articlePermissionService.isAuthor(100, 'admin'))")
  └─ hasRole('ADMIN') = true → 短路求值，直接放行
  └─ 不调用 ArticlePermissionService（性能优化）

访问 /blog/comment（POST，发表评论，阶段四）：
  方法级：@PreAuthorize("hasRole('GUEST')")
  └─ hasRole('GUEST') = true → 放行

访问 /admin/server-monitor/overview（GET）：
  类级：@PreAuthorize("hasRole('ADMIN')")
  └─ hasRole('ADMIN') = true → 放行
```

> 💡 **关键设计点**：SpEL 表达式中的 `or` 是短路求值。`hasRole('ADMIN') or (...)` 一旦 `ADMIN` 为真，就不会调用 `@articlePermissionService`，避免不必要的数据库查询。这也是为何 `isAuthor` 校验只对纯 AUTHOR 角色生效，对 ADMIN 自动跳过。

### 3.10 关键身份对象传递路径

```text
HTTP 请求（Cookie: access_token=xxx）
  ↓
CookieBearerTokenResolver.resolve(request)
  → 返回 token 字符串
  ↓
JwtAuthenticationProvider.authenticate()
  → JwtAuthenticationToken（principal=Jwt, authorities=[ROLE_*]）
  ↓
JwtAuthenticationConverter.convert(jwt)
  → authorities 从 jwt.roles claim 转换
  → principal = jwt.getClaim("sub")（用户名字符串）
  ↓
SecurityContextHolder.context.authentication = JwtAuthenticationToken
  ↓
SecurityContextToBaseContextInterceptor.preHandle()
  → 从 SecurityContextHolder 取 Authentication
  → 根据 principal（用户名）查询 SysUser
  → 构造 SecurityUser（含 userId/nickname/userType）
  → 写入 BaseContext.setUserId(userId) / setCurrentId(userId)
  ↓
Controller 方法执行
  → @AuthenticationPrincipal SecurityUser user（Spring 自动注入）
  → 或 BaseContext.getCurrentId()（旧代码兼容）
  ↓
Service 层执行业务
  → user.getUserId() 获取用户 ID
  ↓
OperationLogAspect 后置通知
  → 从 SecurityContextHolder 取 SecurityUser.userId
  → 记录 operation_logs.user_id
```

### 3.11 关键流程对比开发者注意事项

| 流程节点 | 旧代码残留 | 新代码做法 | 注意事项 |
|---------|-----------|----------|---------|
| 获取当前用户 ID | `BaseContext.getCurrentId()` | `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` | 旧代码可保留（由 `SecurityContextToBaseContextInterceptor` 同步），但新代码建议直接用 `@AuthenticationPrincipal` |
| 控制器方法签名 | 无用户参数 | `@AuthenticationPrincipal SecurityUser user` | Spring 自动注入，无需手动取 |
| 登录入口 | `AdminController.login()` | `POST /oauth2/token` | `AdminController.login()` 可保留作为兼容入口，内部转发 |
| 鉴权失败返回 | 拦截器写 JSON | `CustomAuthenticationEntryPoint` / `CustomAccessDeniedHandler` | 返回格式必须与前端约定一致 |
| CORS 配置 | `WebMvcConfiguration` | 保留在 `WebMvcConfiguration` 或迁移到 `ResourceServerConfig` | 必须配置 `allowCredentials=true` 以支持 Cookie 跨域 |
| CSRF | 默认开启 | `csrf.disable()`（无状态 API） | Cookie 模式下需评估是否启用 CSRF Token |

---

## 四、阶段三任务总览

### 4.1 任务依赖关系图

```text
步骤1：数据库改动
  │  （sys_role/sys_user/sys_user_role 初始数据）
  ↓
步骤2：ArticlePermissionService 开发
  │  （依赖 sys_user_role 表已就绪；为步骤5提供 @PreAuthorize 表达式支撑）
  ↓
步骤3：ResourceServerConfig 改造
  │  （第一层 URL 粗粒度控制；独立于步骤2，但建议先做2再做3）
  ↓
步骤4：Controller 类级 @PreAuthorize 注解
  │  （第二层模块控制；为 15 个 Controller 添加注解）
  ↓
步骤5：Controller 方法级 @PreAuthorize 注解
  │  （第三层精细控制；依赖步骤2的 ArticlePermissionService）
  ↓
步骤6：Token Cookie 下发机制
  │  （OAuth2TokenResponseCookieHandler + CookieBearerTokenResolver）
  ↓
步骤7：旧代码清理
  │  （删除 JwtTokenAdminInterceptor / TokenService 等，改造 OperationLogAspect）
  ↓
步骤8：验收测试
   （按角色矩阵逐项验证）
```

### 4.2 步骤概览表

| 步骤 | 批次 | 目标 | 关键产物 | 依赖前置 |
|-----|:----:|------|---------|---------|
| 1 | 第一批次 | 数据库改动 | `docs/DriftingLeaves.sql` 修改 | 阶段二完成 |
| 2 | 第一批次 | 权限服务开发 | `ArticlePermissionService` + Mapper 补充 | 步骤 1 |
| 3 | 第二批次 | URL 粗粒度权限 | `ResourceServerConfig` 改造 | 步骤 1 |
| 4 | 第二批次 | 类级权限注解 | 15 个 Controller 添加类级注解 | 步骤 3 |
| 5 | 第二批次 | 方法级权限注解 | `ArticleController` + `ArticleCommentController` 方法注解 | 步骤 2、4 |
| 6 | 第三批次 | Token Cookie 下发 | `OAuth2TokenResponseCookieHandler` + `CookieBearerTokenResolver` | 步骤 3 |
| 7 | 第三批次 | 旧代码清理 | 删除 4 个旧文件 + 改造 `OperationLogAspect` | 步骤 6 |
| 8 | 第四批次 | 验收测试 | 角色矩阵全量通过 | 步骤 1-7 |

### 4.3 分批实施方案

> **本节目的**：基于依赖关系和风险控制，将 8 个步骤分为 **4 个批次** 实施，每个批次可独立编译、启动、自测，互不破坏前置成果。

#### 4.3.1 分批依赖关系图

```text
第一批次（基础设施搭建，风险最低）      ← 不动现有业务代码
  ├─ 步骤1：数据库改动
  └─ 步骤2：ArticlePermissionService 开发
        ↓
第二批次（核心权限改造）                ← 依赖步骤2的权限服务
  ├─ 步骤3：ResourceServerConfig 改造（URL 粗粒度）
  ├─ 步骤4：Controller 类级 @PreAuthorize（依赖步骤3兜底）
  └─ 步骤5：Controller 方法级 @PreAuthorize（依赖步骤2+步骤4）
        ↓
第三批次（Token 下发 + 旧代码清理）       ← 必须在权限落地后
  ├─ 步骤6：Token Cookie 下发机制
  └─ 步骤7：旧代码清理（依赖步骤6就绪）
        ↓
第四批次（全量验收测试）
  └─ 步骤8：按角色矩阵逐项验证
```

#### 4.3.2 第一批次：基础设施搭建（风险最低）

**目标**：不动现有业务代码，仅新增基础数据和服务。

| 顺序 | 步骤 | 改动文件 | 说明 |
|-----|-----|---------|------|
| 1 | 数据库改动 | `docs/DriftingLeaves.sql` | 4 角色 + 测试账号 + `admin-client` scopes |
| 2 | ArticlePermissionService 开发 | 新建接口 + 实现类 + Mapper 补充 | 纯新增，不改 Controller |

**批次验收点**：
- [ ] 项目能正常编译启动
- [ ] 数据库 `sys_role` 表有 4 条记录
- [ ] `ArticlePermissionServiceImpl` Bean 注入成功
- [ ] 旧业务流程不受影响（旧拦截器仍在工作）

#### 4.3.3 第二批次：核心权限改造

**目标**：完成三层权限架构（URL → 类级 → 方法级），依赖第一批次的 `ArticlePermissionService`。

| 顺序 | 步骤 | 改动文件 | 说明 |
|-----|-----|---------|------|
| 3 | ResourceServerConfig 改造 | `ResourceServerConfig.java` | 第一层 URL 粗粒度 |
| 4 | Controller 类级 @PreAuthorize | 15 个 Controller | 第二层模块控制 |
| 5 | Controller 方法级 @PreAuthorize | `ArticleController` + `ArticleCommentController` + 其他模块写操作 | 第三层精细控制 |

**实施顺序要求**：必须严格按 3 → 4 → 5 顺序，因为方法级依赖类级兜底，类级依赖 URL 兜底。

**批次验收点**：
- [ ] `admin` Token 访问所有接口 → 200
- [ ] `auditor` Token 访问写接口 → 403
- [ ] `author` Token 访问非文章模块 → 403
- [ ] `author` Token 编辑他人文章 → 403
- [ ] `guest` Token 访问 `/admin/**` → 403

#### 4.3.4 第三批次：Token 下发与旧代码清理

**目标**：切换 Token 下发方式，删除被替代的旧代码。

| 顺序 | 步骤 | 改动文件 | 说明 |
|-----|-----|---------|------|
| 6 | Token Cookie 下发机制 | 新建 2 个类 + 修改 2 个配置类 | Cookie + Header 双轨兼容 |
| 7 | 旧代码清理 | 删除 4 个文件 + 改造 `OperationLogAspect` + `WebMvcConfiguration` | 必须在步骤 6 完成后 |

**实施顺序要求**：步骤 6 必须先于步骤 7。Cookie 下发机制就绪后，旧拦截器才能安全删除（避免空窗期无认证机制）。

**步骤 7 内部清理顺序**（每删一个就启动测试一次）：
1. 改造 `OperationLogAspect`（从 SecurityContextHolder 取 user_id）
2. 移除 `WebMvcConfiguration` 中的 `jwtTokenAdminInterceptor` 注册
3. 删除 `JwtTokenAdminInterceptor.java`
4. 删除 `TokenService.java` + `TokenServiceImpl.java`
5. 删除 `EncryptPasswordService.java` + `EncryptPasswordServiceImpl.java`

**批次验收点**：
- [ ] `POST /oauth2/token` 成功后响应头含 `Set-Cookie: access_token=...; HttpOnly`
- [ ] 浏览器自动携带 Cookie 访问受保护接口
- [ ] 仅带 Authorization Header 的请求仍能正常工作
- [ ] 项目能正常启动，无 Bean 装配失败
- [ ] 操作日志 `user_id` 字段记录正确

#### 4.3.5 第四批次：全量验收测试

**目标**：按角色矩阵全量验证，确保权限模型正确落地。

| 顺序 | 步骤 | 内容 |
|-----|-----|------|
| 8 | 验收测试 | 数据库层 + JWT Token + 接口权限 + 多角色组合 + Cookie 下发 + 旧代码清理 |

**批次验收点**：参照 [步骤 8 验收清单](#步骤-8验收测试) 逐项 checkbox 验证。

#### 4.3.6 关键风险点提示

| 批次 | 风险 | 应对 |
|-----|------|------|
| 第一批次 | 数据库脚本执行失败 | 先备份数据库，分块执行 SQL |
| 第二批次 | SpEL 表达式写错导致全 403 | 先用 `hasRole('ADMIN')` 跑通，再逐步加复杂表达式 |
| 第三批次 | 删除旧代码导致启动失败 | 逐文件删除，每删一个就启动测试 |
| 第三批次 | Cookie 与 CORS 冲突 | 确认 `allowCredentials=true`，本地用 SameSite=Lax 调试 |
| 第四批次 | 验收项过多易遗漏 | 按角色分组测试，每组完成后再进入下一组 |

#### 4.3.7 执行节奏建议

- **每次只实施一个批次**，批次内步骤可连续执行
- **每个批次结束后提交一次 Git**，便于回滚
- **批次之间等待确认**，确保前置成果稳定后再进入下一批次
- 每个步骤完成后必须执行 `mvn clean package -pl DL-server -am` 编译验证，并启动服务做接口冒烟测试

---

## 五、分步实施计划

### 步骤 1：数据库改动

**目标**：将 `sys_role` 表初始化为 4 个正交角色，并准备测试账号。

**改动文件**：`docs/DriftingLeaves.sql`

**操作内容**：

1. **修改 `sys_role` 初始数据**（替换原 3 角色为 4 角色）：

```sql
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN',   '超级管理员', '系统最高权限，包括用户管理、配置、审核、监控等'),
('AUTHOR',  '内容创作者', '文章发布与管理，仅限自己的文章'),
('AUDITOR', '后台审计员', '后台只读权限，可查看文章、日志和统计，不可修改'),
('GUEST',   '普通访客',   '前台交互权限，可评论、点赞、留言、订阅');
```

2. **修改 `sys_user.user_type` 字段注释**（弱化语义，实际权限以 `sys_user_role` 为准）：

```sql
user_type TINYINT NOT NULL DEFAULT 1 COMMENT '用户主身份（仅展示用）：1普通访客 2管理员 3审计员 4创作者。实际权限以 sys_user_role 为准',
```

3. **插入 3 个测试账号并关联角色**：
   - `admin`（ADMIN + AUTHOR + GUEST 三重身份，模拟站长）
   - `auditor`（AUDITOR 单角色）
   - `author`（AUTHOR + GUEST 双角色）

4. **调整 `admin-client` 的 scopes**（追加 `author`、`auditor`）：

```sql
UPDATE oauth2_registered_client 
SET scopes = 'openid,profile,admin,author,auditor' 
WHERE client_id = 'admin-client';
```

**验收点**：
- [ ] 数据库中 `sys_role` 表有 4 条记录
- [ ] `admin` 账号在 `sys_user_role` 中关联 3 个角色
- [ ] `auditor` 账号关联 1 个角色
- [ ] `author` 账号关联 2 个角色
- [ ] `admin-client` 的 scopes 包含 `admin,author,auditor`

---

### 步骤 2：ArticlePermissionService 开发

**目标**：为 AUTHOR 角色的数据范围校验提供 SpEL 调用入口。

**依赖**：步骤 1（`sys_user_role` 表已就绪）、`article_authors` 表已存在。

**新建文件**：
- `DL-server/src/main/java/com/xuan/service/ArticlePermissionService.java`
- `DL-server/src/main/java/com/xuan/service/impl/ArticlePermissionServiceImpl.java`

**修改文件**：
- `DL-server/src/main/java/com/xuan/mapper/ArticleAuthorsMapper.java`（补充 3 个查询方法）

**接口设计**：

```java
public interface ArticlePermissionService {
    boolean isAuthor(Long articleId, String username);              // 任意作者（含共同作者）
    boolean isFirstAuthor(Long articleId, String username);         // 仅第一作者
    boolean isFirstAuthor(List<Long> articleIds, String username); // 批量第一作者
    boolean isCommentInOwnArticle(Long commentId, String username);
    boolean areCommentsInOwnArticle(List<Long> commentIds, String username);
}
```

**Mapper 补充方法**：

```java
@Select("SELECT COUNT(1) FROM article_authors WHERE article_id = #{articleId} AND user_id = #{userId} AND invite_status = 1")
boolean existsByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

@Select("SELECT COUNT(1) FROM article_authors WHERE article_id = #{articleId} AND user_id = #{userId} AND author_role = 1 AND invite_status = 1")
boolean existsFirstAuthorByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

@Select("<script>SELECT COUNT(1) FROM article_authors WHERE user_id = #{userId} AND author_role = 1 AND invite_status = 1 AND article_id IN <foreach collection='articleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
Long countFirstAuthorByArticleIdsAndUserId(@Param("articleIds") List<Long> articleIds, @Param("userId") Long userId);
```

**验收点**：
- [ ] 项目能正常编译
- [ ] Bean 注入成功（`ArticlePermissionServiceImpl` 被加载）
- [ ] 手动测试 `isAuthor` / `isFirstAuthor` 返回正确结果

---

### 步骤 3：ResourceServerConfig 改造

**目标**：在第一层 URL 粗粒度控制中，按 HTTP 方法和路径模式拒绝非法请求。

**改动文件**：`DL-server/src/main/java/com/xuan/resource/config/ResourceServerConfig.java`

**关键配置**：

```java
.authorizeHttpRequests(auth -> auth
    // 公开端点（无需认证）
    .requestMatchers("/oauth2/**",
                      "/admin/admin/login",
                      "/admin/admin/sendCode",
                      "/admin/admin/logout").permitAll()

    // 敏感路径：仅 ADMIN
    .requestMatchers("/admin/server-monitor/**").hasRole("ADMIN")

    // 后台 GET 请求：ADMIN + AUTHOR + AUDITOR（GUEST 排除）
    .requestMatchers(HttpMethod.GET, "/admin/**").hasAnyRole("ADMIN", "AUTHOR", "AUDITOR")

    // 后台非 GET 请求：ADMIN + AUTHOR（AUDITOR/GUEST 排除，AUTHOR 在方法级再细控）
    .requestMatchers(HttpMethod.POST,   "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
    .requestMatchers(HttpMethod.PUT,    "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
    .requestMatchers(HttpMethod.DELETE, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")

    // 其他路径：暂时放行（blog/cv/home 在阶段四处理）
    .anyRequest().permitAll()
)
```

**验收点**：
- [ ] 未登录访问 `/admin/article/page` → 401
- [ ] 未登录访问 `/admin/admin/login`（POST）→ 不被拦截
- [ ] `auditor` Token 访问 `/admin/article`（POST）→ 403（被第一层拦截）
- [ ] `guest` Token 访问任意 `/admin/**` GET → 403

---

### 步骤 4：Controller 类级 @PreAuthorize 注解

**目标**：通过类级注解，将 AUTHOR 排除在非文章模块之外。

**改动文件**：15 个 Controller（位于 `DL-server/src/main/java/com/xuan/controller/admin/`）

**注解映射表**：

| Controller | 类级注解 | 排除角色 |
|-----------|---------|---------|
| `MessageController` | `@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")` | AUTHOR |
| `FriendLinkController` | 同上 | AUTHOR |
| `MusicController` | 同上 | AUTHOR |
| `PersonalInfoController` | 同上 | AUTHOR |
| `ExperienceController` | 同上 | AUTHOR |
| `SkillController` | 同上 | AUTHOR |
| `SocialMediaController` | 同上 | AUTHOR |
| `SystemConfigController` | 同上 | AUTHOR |
| `VisitorController` | 同上 | AUTHOR |
| `ViewController` | 同上 | AUTHOR |
| `OperationLogController` | 同上 | AUTHOR |
| `RssSubscriptionController` | 同上 | AUTHOR |
| `ReportController` | 同上 | AUTHOR |
| `ArticleCategoryController` | 同上 | AUTHOR（只能读分类，不能管理） |
| `ArticleTagController` | 同上 | AUTHOR（只能读标签，不能管理） |
| `ServerMonitorController` | `@PreAuthorize("hasRole('ADMIN')")` | AUTHOR + AUDITOR |

**不需要类级注解的 Controller**：
- `ArticleController`（由方法级细控）
- `ArticleCommentController`（由方法级细控）
- `CommonController`（文件上传，ADMIN + AUTHOR 都需要）
- `AdminController`（登录端点 permitAll）

**验收点**：
- [ ] `author` Token 访问 `/admin/message/page`（GET）→ 403
- [ ] `author` Token 访问 `/admin/articleCategory`（GET）→ 403
- [ ] `auditor` Token 访问 `/admin/server-monitor/overview` → 403

---

### 步骤 5：Controller 方法级 @PreAuthorize 注解

**目标**：实现 AUTHOR 数据范围校验（仅自己的文章）+ ADMIN 专属操作（审核/置顶）。

**依赖**：步骤 2（`ArticlePermissionService` 已就绪）。

**改动文件**：
- `DL-server/src/main/java/com/xuan/controller/admin/ArticleController.java`
- `DL-server/src/main/java/com/xuan/controller/admin/ArticleCommentController.java`
- 其他管理类 Controller 的写操作方法（追加 `@PreAuthorize("hasRole('ADMIN')")`）

#### 5.1 ArticleController 方法级注解

| 方法 | HTTP | 注解 |
|------|------|------|
| `pageQuery` | GET `/page` | `@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")` |
| `getArticleById` | GET `/{id}` | 同上（AUTHOR 在 Service 层过滤非自己文章） |
| `search` | GET `/search` | 同上 |
| `createArticle` | POST | `@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")` |
| `updateArticle` | PUT | `@PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isAuthor(#articleDTO.id, authentication.name))")` |
| `batchDelete` | DELETE | `@PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isFirstAuthor(#ids, authentication.name))")` |
| `updateStatus` | PUT `/status/{id}` | `@PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isAuthor(#id, authentication.name))")` |
| `toggleTop` | PUT `/top/{id}` | `@PreAuthorize("hasRole('ADMIN')")` |

> ⚠️ 若 `updateStatus` 涉及审核状态流转（如变更为已发布/违规），建议拆出独立 `PUT /audit/{id}` 端点，方法级仅限 `hasRole('ADMIN')`。

#### 5.2 ArticleCommentController 方法级注解

| 方法 | HTTP | 注解 |
|------|------|------|
| `pageQuery` | GET `/page` | `@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")` |
| `getByArticleId` | GET `/{articleId}` | 同上 |
| `batchApprove` | PUT `/approve` | `@PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.areCommentsInOwnArticle(#ids, authentication.name))")` |
| `batchDelete` | DELETE | 同上 |
| `adminReply` | POST `/reply` | `@PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isCommentInOwnArticle(#articleCommentReplyDTO.parentId, authentication.name))")` |

#### 5.3 管理类模块的写操作

对于类级已限制为 `ADMIN + AUDITOR` 的模块，所有非 GET 方法追加：

```java
@PreAuthorize("hasRole('ADMIN')")
```

涉及 Controller：`MessageController`、`FriendLinkController`、`MusicController`、`PersonalInfoController`、`ExperienceController`、`SkillController`、`SocialMediaController`、`SystemConfigController`、`VisitorController`、`ViewController`、`OperationLogController`、`RssSubscriptionController`、`ReportController`、`ArticleCategoryController`、`ArticleTagController`。

**验收点**：
- [ ] `author` Token 编辑他人文章 → 403
- [ ] `author` Token 删除他人文章 → 403
- [ ] `author` Token 置顶文章 → 403
- [ ] `auditor` Token 调用任意写接口 → 403
- [ ] `admin` Token 全部接口 → 200

---

### 步骤 6：Token Cookie 下发机制

**目标**：在 SAS Token Endpoint 颁发 Token 成功后，将 access_token / refresh_token 写入 HttpOnly Cookie，并让 Resource Server 支持从 Cookie 解析 Token。

**新建文件**：
- `DL-server/src/main/java/com/xuan/auth/security/OAuth2TokenResponseCookieHandler.java`（实现 `AuthenticationSuccessHandler`）
- `DL-server/src/main/java/com/xuan/resource/config/CookieBearerTokenResolver.java`（实现 `BearerTokenResolver`）

**修改文件**：
- `DL-server/src/main/java/com/xuan/auth/config/AuthorizationServerConfig.java`（注册 `accessTokenResponseHandler`）
- `DL-server/src/main/java/com/xuan/resource/config/ResourceServerConfig.java`（注册 `bearerTokenResolver`）

**Cookie 配置规范**：
- 名称：`access_token` / `refresh_token`
- 属性：`HttpOnly; Max-Age=...; Path=/; SameSite=Strict`
- 本地开发：不强制 `Secure`（HTTP 调试用）
- 生产环境：必须 `Secure`（HTTPS）

**CookieBearerTokenResolver 解析优先级**：
1. 优先从 `Authorization: Bearer xxx` Header 解析
2. 其次从 `access_token` Cookie 解析
3. 两者都没有则返回 `null`（触发 401）

**验收点**：
- [ ] `POST /oauth2/token` 成功后，响应头包含 `Set-Cookie: access_token=...; HttpOnly`
- [ ] 浏览器后续请求自动携带 Cookie
- [ ] 旧的无 Cookie 请求（仅带 Authorization Header）仍能正常工作
- [ ] `POST /oauth2/token` 仍返回 JSON 响应体（双轨兼容）

---

### 步骤 7：旧代码清理

**目标**：删除已被 Resource Server 接管的旧认证代码，避免双套机制冲突。

**删除文件**（4 个）：
- `DL-server/src/main/java/com/xuan/interceptor/JwtTokenAdminInterceptor.java`
- `DL-server/src/main/java/com/xuan/service/TokenService.java`
- `DL-server/src/main/java/com/xuan/service/impl/TokenServiceImpl.java`
- `DL-server/src/main/java/com/xuan/service/EncryptPasswordService.java`（及其 `EncryptPasswordServiceImpl.java`）

**修改文件**：
- `DL-server/src/main/java/com/xuan/config/WebMvcConfiguration.java`：移除 `jwtTokenAdminInterceptor` 字段及 `addInterceptors` 中的注册
- `DL-server/src/main/java/com/xuan/aspect/OperationLogAspect.java`：从 `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` 取 `SecurityUser`，再获取 `user_id`，替代原 `BaseContext.getCurrentId()`

**OperationLogAspect 改造示例**：

```java
private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof SecurityUser)) {
        return null;
    }
    return ((SecurityUser) auth.getPrincipal()).getUserId();
}
```

> ⚠️ 清理策略：**逐文件删除，每删一个就编译 + 启动一次**，确保不破坏项目。

**验收点**：
- [ ] 项目能正常启动，无 Bean 装配失败
- [ ] `WebMvcConfiguration` 中无 `jwtTokenAdminInterceptor` 引用
- [ ] 操作日志记录的 `user_id` 正确（登录后访问接口，查 `operation_logs` 表）
- [ ] 旧路径（如 `/admin/admin/login` 内部不再调用 `TokenService`）已切换到 `/oauth2/token`

---

### 步骤 8：验收测试

**目标**：按角色矩阵全量验证，确保权限模型正确落地。

#### 8.1 数据库层验收

- [ ] `sys_role` 表包含 4 个角色：ADMIN、AUTHOR、AUDITOR、GUEST
- [ ] `sys_user.user_type` 字段注释已更新
- [ ] `admin` 账号关联 ADMIN + AUTHOR + GUEST
- [ ] `auditor` 账号关联 AUDITOR
- [ ] `author` 账号关联 AUTHOR + GUEST

#### 8.2 JWT Token 验收

- [ ] `admin` 登录后 JWT 的 `roles` claim = `["ROLE_ADMIN", "ROLE_AUTHOR", "ROLE_GUEST"]`
- [ ] `auditor` 登录后 JWT 的 `roles` claim = `["ROLE_AUDITOR"]`
- [ ] `author` 登录后 JWT 的 `roles` claim = `["ROLE_AUTHOR", "ROLE_GUEST"]`

#### 8.3 接口权限验收

**ADMIN 角色**：
- [ ] `GET /admin/article/page` → 200
- [ ] `POST /admin/article` → 200
- [ ] `PUT /admin/article/top/{id}` → 200（置顶）
- [ ] `GET /admin/server-monitor/overview` → 200
- [ ] `GET /admin/operationLog/page` → 200
- [ ] `DELETE /admin/operationLog` → 200

**AUTHOR 角色**（仅 AUTHOR + GUEST）：
- [ ] `GET /admin/article/page` → 200（仅返回自己的文章）
- [ ] `POST /admin/article` → 200
- [ ] `PUT /admin/article`（自己的文章）→ 200
- [ ] `PUT /admin/article`（他人文章）→ 403
- [ ] `PUT /admin/article/top/{id}` → 403（不能置顶）
- [ ] `GET /admin/server-monitor/overview` → 403
- [ ] `GET /admin/message/page` → 403（不能访问留言模块）
- [ ] `GET /admin/operationLog/page` → 403
- [ ] `GET /admin/articleCategory` → 403（类级注解排除）

**AUDITOR 角色**：
- [ ] `GET /admin/article/page` → 200
- [ ] `GET /admin/message/page` → 200
- [ ] `GET /admin/operationLog/page` → 200
- [ ] `GET /admin/server-monitor/overview` → 403（敏感信息）
- [ ] `POST /admin/article` → 403
- [ ] `PUT /admin/article` → 403
- [ ] `DELETE /admin/article` → 403

**GUEST 角色**：
- [ ] `GET /admin/article/page` → 403（不进后台）
- [ ] `POST /admin/article` → 403
- [ ] 所有 `/admin/**` → 403

**未登录**：
- [ ] 所有 `/admin/**`（除 login/sendCode/logout）→ 401
- [ ] `POST /admin/admin/login` → permitAll 通过

#### 8.4 多角色组合验收

- [ ] `admin`（ADMIN + AUTHOR + GUEST）能访问所有后台接口 + 发文章
- [ ] `author`（AUTHOR + GUEST）能发文章 + 前台交互，但不能访问其他后台模块
- [ ] `auditor`（AUDITOR）能查看后台数据，但不能修改

#### 8.5 Cookie 下发验收

- [ ] `POST /oauth2/token` 成功后，响应头包含 `Set-Cookie: access_token=...; HttpOnly`
- [ ] 浏览器后续请求自动携带 Cookie
- [ ] 旧的无 Cookie 请求（仅带 Authorization Header）仍能正常工作

#### 8.6 旧代码清理验收

- [ ] `JwtTokenAdminInterceptor.java` 已删除
- [ ] `TokenService.java` 已删除
- [ ] `WebMvcConfiguration.java` 中无 `jwtTokenAdminInterceptor` 引用
- [ ] 项目能正常启动，无 Bean 装配失败
- [ ] 操作日志记录的 `user_id` 正确

---

## 六、关键文件清单

### 6.1 需要修改的文件

| 文件路径 | 修改内容 |
|---------|---------|
| `docs/DriftingLeaves.sql` | `sys_role` 初始数据、`sys_user.user_type` 注释、测试账号、`admin-client` scopes |
| `DL-server/.../resource/config/ResourceServerConfig.java` | URL 粗粒度权限 + 注册 `CookieBearerTokenResolver` |
| `DL-server/.../auth/config/AuthorizationServerConfig.java` | 注册 `accessTokenResponseHandler` |
| `DL-server/.../controller/admin/*.java`（15 个） | 类级 `@PreAuthorize` |
| `DL-server/.../controller/admin/ArticleController.java` | 方法级 `@PreAuthorize` |
| `DL-server/.../controller/admin/ArticleCommentController.java` | 方法级 `@PreAuthorize` |
| `DL-server/.../mapper/ArticleAuthorsMapper.java` | 补充 3 个查询方法 |
| `DL-server/.../config/WebMvcConfiguration.java` | 移除 `jwtTokenAdminInterceptor` 引用 |
| `DL-server/.../aspect/OperationLogAspect.java` | 从 `SecurityContextHolder` 取 `user_id` |

### 6.2 需要新建的文件

| 文件路径 | 用途 |
|---------|------|
| `DL-server/.../service/ArticlePermissionService.java` | 文章权限校验接口 |
| `DL-server/.../service/impl/ArticlePermissionServiceImpl.java` | 文章权限校验实现 |
| `DL-server/.../auth/security/OAuth2TokenResponseCookieHandler.java` | Token Cookie 下发 |
| `DL-server/.../resource/config/CookieBearerTokenResolver.java` | Cookie Token 解析 |

### 6.3 需要删除的文件

| 文件路径 | 原因 |
|---------|------|
| `DL-server/.../interceptor/JwtTokenAdminInterceptor.java` | 已由 Resource Server 接管 |
| `DL-server/.../service/TokenService.java` | 旧 JWT 服务 |
| `DL-server/.../service/impl/TokenServiceImpl.java` | 旧 JWT 服务实现 |
| `DL-server/.../service/EncryptPasswordService.java` | 旧盐值加密 |
| `DL-server/.../service/impl/EncryptPasswordServiceImpl.java` | 旧盐值加密实现 |

---

## 七、风险与应对

| 风险 | 应对措施 | 回滚方案 |
|------|---------|---------|
| AUTHOR 数据范围校验失效，导致越权访问 | Service 层增加二次校验，不仅依赖 `@PreAuthorize` | 临时将 AUTHOR 权限降级为 AUDITOR |
| 多角色组合导致权限判断混乱 | 严格遵循"角色正交"原则，避免角色间权限重叠 | 简化为单角色模式 |
| `ArticlePermissionService` 性能问题 | 后续增加 Redis 缓存，缓存用户与文章的关联关系 | 临时关闭数据范围校验 |
| Cookie 下发与 CORS 冲突 | 确保 CORS 配置允许 `allowCredentials=true` | 回退到 Authorization Header 方式 |
| 旧代码清理导致启动失败 | 分批清理，每删一个文件就启动测试 | 从 Git 历史恢复 |
| SAS `accessTokenResponseHandler` 扩展点不可用 | 回退到方案 B：保留 `/admin/admin/login` Controller，内部调用 `/oauth2/token` 后写 Cookie | 删除 Cookie 下发，仅用 Header |
| 步骤 5 方法级 `@PreAuthorize` SpEL 表达式写错导致全 403 | 先用最简单的 `hasRole('ADMIN')` 跑通，再逐步加复杂表达式 | 临时移除方法级注解，依赖类级兜底 |

---

## 八、提交建议

每个步骤结束后提交一次 Git，commit message 模板：

```text
feat(security): 阶段三步骤N - XXX

- 改动1
- 改动2
- 验收点：xxx
```

示例：

```text
feat(security): 阶段三步骤1 - 数据库 RBAC 角色初始化

- sys_role 表插入 ADMIN/AUTHOR/AUDITOR/GUEST 4 个角色
- 新增 admin/auditor/author 3 个测试账号并关联角色
- admin-client scopes 追加 author/auditor
- 验收点：JWT roles claim 包含多角色
```

---

## 九、与后续阶段的衔接

### 9.1 与阶段四（博客端登录 + 交互接口改造）的衔接

阶段三为阶段四提供以下基础：
1. **GUEST 角色已定义**：博客端登录用户直接使用 `GUEST` 角色
2. **OAuth2 客户端已预置**：`blog-client` 已在 `oauth2_registered_client` 表中
3. **JWT 多角色支持**：`ResourceServerConfig` 已支持多角色解析
4. **权限框架已就绪**：阶段四仅需在 `/blog/**` 接口添加 `@PreAuthorize("hasRole('GUEST')")`

### 9.2 与阶段五（前端适配）的衔接

阶段三的多角色设计对前端的影响：
1. **菜单动态渲染**：前端根据 JWT 的 `roles` claim 动态显示菜单
   - 有 `ROLE_ADMIN` → 显示"系统管理"菜单
   - 有 `ROLE_AUTHOR` → 显示"我的文章"菜单
   - 有 `ROLE_AUDITOR` → 显示"数据中心"菜单
   - 仅有 `ROLE_GUEST` → 不显示后台入口
2. **登录页调整**：登录后根据角色跳转不同页面
3. **Token 刷新**：前端统一处理 401 自动刷新，无需区分角色

---

## 十、总结

本计划书将阶段三拆分为 8 个可独立验收的步骤，遵循"**先数据库、再服务、后配置、最后清理**"的顺序：

1. **步骤 1-2**：基础设施（数据库 + 权限服务）
2. **步骤 3-5**：权限落地（URL 粗粒度 → 类级 → 方法级三层控制）
3. **步骤 6**：Token 下发（Cookie 双轨兼容）
4. **步骤 7**：旧代码清理（避免双套机制冲突）
5. **步骤 8**：全量验收（按角色矩阵逐项验证）

4 个角色（ADMIN / AUTHOR / AUDITOR / GUEST）正交设计，通过 `sys_user_role` 多对多关联实现复合身份，AUTHOR 的数据范围限制通过 `ArticlePermissionService` 在 `@PreAuthorize` SpEL 表达式中调用实现，确保作者只能操作自己的文章。

**关键业务流程变革**（详见第三章）：
- 登录入口从 `POST /admin/admin/login` 迁移到 `POST /oauth2/token`，由 SAS 统一处理
- 鉴权从硬编码拦截器升级为三层架构（URL → 类级 → 方法级）
- 身份传递从 `BaseContext` ThreadLocal 升级为 `SecurityContextHolder` + `@AuthenticationPrincipal`
- 操作日志 user_id 从 `BaseContext.getCurrentId()` 改为从 `SecurityContextHolder` 取 `SecurityUser.userId`
- Token 校验从 Redis 白名单改为 JWKS 离线验签（性能更好，解耦授权与资源服务）
- 多角色通过 SpEL 短路求值自动叠加，权限取并集

每一步完成后必须执行 `mvn clean package -pl DL-server -am` 编译验证，并启动服务做接口冒烟测试，确保不破坏前置步骤的成果。
