# DriftingLeaves v1 vs v2 差异分析

## 1. API接口差异

### 1.1 移除的接口（v1有，v2无）

| 模块 | 接口路径 | 方法 | 说明 |
|------|----------|------|------|
| admin | `/admin/admin/login` | POST | 管理员登录（v2改用OAuth2端点） |
| admin | `/admin/admin/sendCode` | POST | 发送验证码 |
| admin | `/admin/admin/logout` | POST | 管理员退出登录 |
| admin | `/admin/admin` | GET | 获取管理员信息 |
| admin | `/admin/admin/changePassword` | PUT | 修改密码 |
| admin | `/admin/admin/changeNickname` | PUT | 更改昵称 |
| admin | `/admin/admin/changeEmail` | PUT | 换绑邮箱 |

### 1.2 新增的接口（v2新增）

| 模块 | 接口路径 | 方法 | 说明 |
|------|----------|------|------|
| blog | `/blog/auth/sendCode` | POST | 博客端发送邮箱验证码 |
| blog | `/blog/auth/register` | POST | 博客端用户注册 |
| OAuth2 | `/oauth2/token` | POST | OAuth2令牌端点（支持多种grant_type） |
| OAuth2 | `/oauth2/jwks` | GET | JWKS公钥端点 |
| OAuth2 | `/oauth2/authorize` | GET/POST | OAuth2授权端点 |
| OAuth2 | `/oauth2/introspect` | POST | 令牌内省端点 |
| OAuth2 | `/oauth2/revoke` | POST | 令牌吊销端点 |
| OAuth2 | `/login/oauth2/code/{registrationId}` | GET | 第三方OAuth2回调端点 |

### 1.3 接口路径变化

| v1路径 | v2路径 | 变化说明 |
|--------|--------|----------|
| `/admin/admin/login` | `/oauth2/token` | 登录入口迁移到OAuth2端点 |
| `/admin/admin/sendCode` | `/blog/auth/sendCode` | 博客端验证码接口独立 |
| `/admin/admin/logout` | `/oauth2/revoke` | 登出改用令牌吊销 |

### 1.4 自定义Grant Type

v2新增两种自定义授权类型：

1. **admin_password_code**（管理端）
   - 参数：`username`, `password`, `code`
   - 用于管理员用户名+密码+验证码登录

2. **email_code**（博客端）
   - 参数：`email`, `code`
   - 用于博客用户邮箱+验证码登录

## 2. 数据库表结构差异

### 2.1 移除的表

| 表名 | 说明 |
|------|------|
| `admin` | 合并到`sys_user`表 |

### 2.2 新增的表

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户表（替代admin表） |
| `sys_role` | 角色表（ADMIN/AUTHOR/AUDITOR/GUEST） |
| `sys_user_role` | 用户角色关联表（多对多） |
| `sys_permission` | 权限表（预留，用于细粒度控制） |
| `sys_role_permission` | 角色权限关联表 |
| `sys_login_lock` | 登录锁定记录表 |
| `oauth2_registered_client` | OAuth2客户端注册表 |
| `oauth2_authorization` | OAuth2授权记录表 |
| `oauth2_authorization_consent` | OAuth2授权同意记录表 |
| `article_authors` | 文章-作者关联表（多作者协作） |
| `article_audit_logs` | 文章审核记录表 |

### 2.3 字段变化

#### sys_user（替代admin）
- 移除：`salt`, `role`
- 新增：`user_type`, `status`, `login_type`, `oauth_id`, `oauth_provider`, `last_login_time`, `last_login_ip`
- 主键：`INT` → `BIGINT`

#### articles
- `is_published` → `status`（0草稿 1待审核 2已发布 3违规）
- 新增：`submit_time`（提交审核时间）

#### article_comments
- 移除：`visitor_id`, `nickname`, `email_or_qq`
- 新增：`user_id`（非空，必须登录）
- `is_secret`含义：从"是否匿名"改为"是否悄悄话"

#### article_likes
- 移除：`visitor_id`
- 新增：`user_id`（非空，必须登录）

#### messages
- 移除：`visitor_id`, `nickname`, `email_or_qq`
- 新增：`user_id`（非空，必须登录）
- `is_secret`含义：从"是否匿名"改为"是否悄悄话"

#### rss_subscriptions
- 移除：`visitor_id`
- 新增：`user_id`（非空，必须登录）

#### operation_logs
- `admin_id` → `user_id`（非空）

### 2.4 主键统一

所有表主键从`INT`改为`BIGINT`，便于分布式扩展。

## 3. 配置文件差异

### 3.1 application.yml新增配置

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:5922  # JWT签发者URI
```

### 3.2 application-dev.yml新增配置

```yaml
dl:
  oauth2:
    github:
      client-id: ""       # GitHub OAuth App Client ID
      client-secret: ""   # GitHub OAuth App Client Secret
    gitee:
      client-id: ""       # Gitee应用Client ID
      client-secret: ""   # Gitee应用Client Secret
  oauth2-redirect:
    success-url: http://localhost:5173/        # 登录成功后跳转
    failure-url: http://localhost:5173/login   # 登录失败后跳转
```

## 4. 前端需要关注的变化

### 4.1 登录流程变化

#### v1登录流程
```
1. POST /admin/admin/sendCode  → 发送验证码
2. POST /admin/admin/login     → 登录，返回Token
3. Token写入HttpOnly Cookie
```

#### v2登录流程（管理端）
```
1. POST /admin/admin/sendCode  → 发送验证码（保留兼容）
2. POST /oauth2/token          → 登录
   - grant_type: admin_password_code
   - client_id: admin-client
   - username: xxx
   - password: xxx
   - code: xxx
3. 响应同时返回JSON和HttpOnly Cookie
```

#### v2登录流程（博客端）
```
1. POST /blog/auth/sendCode    → 发送邮箱验证码
2. POST /blog/auth/register    → 用户注册（首次）
3. POST /oauth2/token          → 登录
   - grant_type: email_code
   - client_id: blog-client
   - email: xxx
   - code: xxx
```

### 4.2 Token管理变化

| 项目 | v1 | v2 |
|------|----|----|
| Token类型 | 自定义JWT | OAuth2 JWT（Spring Authorization Server） |
| 签名算法 | HMAC-SHA256 | RSA（JWKS） |
| Token存储 | HttpOnly Cookie | HttpOnly Cookie + Authorization Header（双轨） |
| Token刷新 | 无 | 支持RefreshToken |
| Token吊销 | 无 | 支持`/oauth2/revoke` |
| Token验证 | 本地验证 | 本地验证（JWKS离线验签） |

### 4.3 权限控制变化

#### v1权限模型
- 管理员（role=1）
- 游客（role=0）：只允许GET查询

#### v2权限模型（RBAC）
- **ADMIN**：超级管理员，所有权限
- **AUTHOR**：内容创作者，文章发布与管理
- **AUDITOR**：后台审计员，只读权限
- **GUEST**：普通访客，前台交互权限（评论、点赞、留言等）

#### URL权限规则（v2）
```
/oauth2/**                          → permitAll
/admin/server-monitor/**            → hasRole('ADMIN')
GET /admin/**                       → hasAnyRole('ADMIN','AUTHOR','AUDITOR')
POST/PUT/DELETE /admin/**           → hasAnyRole('ADMIN','AUTHOR')
/blog/auth/**                       → permitAll
GET /blog/**                        → permitAll
POST/PUT/DELETE /blog/**            → hasRole('GUEST')
```

### 4.4 新增的OAuth2相关接口

#### 第三方登录（GitHub/Gitee）
```
1. 前端重定向到：/oauth2/authorization/{registrationId}
   - registrationId: github 或 gitee
2. 用户在第三方平台授权
3. 回调到：/login/oauth2/code/{registrationId}
4. 后端自动创建/关联用户，重定向到前端首页
```

#### 令牌刷新
```
POST /oauth2/token
- grant_type: refresh_token
- client_id: xxx
- refresh_token: xxx
```

#### 令牌吊销
```
POST /oauth2/revoke
- token: xxx
- token_type_hint: refresh_token
```

### 4.5 前端适配要点

1. **登录接口改造**
   - 管理端：`/admin/admin/login` → `/oauth2/token`
   - 博客端：新增`/blog/auth/register`和`/blog/auth/sendCode`

2. **Token处理**
   - 响应同时包含JSON和Cookie
   - 前端可选择从Cookie或Header获取Token
   - 刷新Token需要保存`refresh_token`

3. **权限判断**
   - 需要从JWT中解析`roles`字段
   - 根据角色控制UI显示/隐藏

4. **第三方登录**
   - 需要处理OAuth2回调页面
   - 需要处理登录成功/失败重定向

5. **错误处理**
   - 401：未认证（Token无效或过期）
   - 403：权限不足（角色不匹配）

## 5. 迁移建议

### 5.1 数据库迁移
1. 创建新表（sys_user、sys_role等）
2. 迁移admin表数据到sys_user
3. 初始化角色数据
4. 配置OAuth2客户端

### 5.2 前端迁移
1. 修改登录接口为OAuth2端点
2. 处理Token响应（同时支持Cookie和Header）
3. 实现Token刷新逻辑
4. 添加第三方登录入口
5. 处理权限控制UI

### 5.3 后端迁移
1. 配置Spring Authorization Server
2. 实现自定义Grant Type
3. 配置Resource Server
4. 迁移业务逻辑到新的权限模型
