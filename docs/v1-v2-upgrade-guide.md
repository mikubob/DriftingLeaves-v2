# DriftingLeaves v1 → v2 升级指南

> 本文档面向前端开发人员，详细说明 DriftingLeaves 博客系统从 v1 升级到 v2 的所有变化，包括架构变更、API 接口变化、认证授权机制变化、数据库表结构变化等。

---

## 目录

1. [项目概览](#1-项目概览)
2. [技术栈对比](#2-技术栈对比)
3. [架构变更总览](#3-架构变更总览)
4. [认证授权体系变化（重点）](#4-认证授权体系变化重点)
5. [API 接口变化](#5-api-接口变化)
6. [数据库表结构变化](#6-数据库表结构变化)
7. [前端登录流程改造](#7-前端登录流程改造)
8. [前端权限控制改造](#8-前端权限控制改造)
9. [新增功能模块](#9-新增功能模块)
10. [配置文件变化](#10-配置文件变化)
11. [常见问题 FAQ](#11-常见问题-faq)

---

## 1. 项目概览

| 项目 | v1 | v2 |
|------|----|----|
| 仓库地址 | [mikubob/DriftingLeaves](https://github.com/mikubob/DriftingLeaves) | [mikubob/DriftingLeaves-v2](https://github.com/mikubob/DriftingLeaves-v2) |
| 提交次数 | 90 commits | 9 commits |
| 项目类型 | Spring Boot 多模块 Maven 项目 | Spring Boot 多模块 Maven 项目 |
| 主要升级 | - | OAuth2 授权服务器 + RBAC 权限模型 |

### 模块结构（两个版本相同）

```
DriftingLeaves/
├── DL-common/    # 公共工具模块（常量、异常、工具类、配置属性）
├── DL-pojo/      # 数据对象模块（Entity、DTO、VO）
├── DL-server/    # 服务主模块（Controller、Service、Mapper、配置）
├── docs/         # 文档
└── pom.xml       # 父 POM
```

---

## 2. 技术栈对比

| 类别 | v1 | v2 | 变化说明 |
|------|----|----|----------|
| **框架** | Spring Boot 3.5.7 | Spring Boot 3.5.7 | 无变化 |
| **Java** | 21 | 21 | 无变化 |
| **安全框架** | 自定义 JWT 拦截器 | Spring Authorization Server + OAuth2 | **重大变化** |
| **认证方式** | 自定义 JWT + Redis | OAuth2 JWT（自包含） | **重大变化** |
| **权限模型** | 管理员/游客二元模型 | RBAC 四角色模型 | **重大变化** |
| **密码加密** | 自定义盐值加密 | BCrypt 标准编码 | **重大变化** |
| **第三方登录** | 不支持 | GitHub / Gitee OAuth2 | **新增** |
| **ORM** | MyBatis-Plus 3.5.7 | MyBatis-Plus 3.5.7 | 无变化 |
| **数据库** | MySQL 8.0.33 | MySQL 8.0.33 | 无变化 |
| **缓存** | Redis + Caffeine | Redis + Caffeine | 无变化 |
| **限流** | Bucket4j | Bucket4j | 无变化 |
| **对象存储** | 阿里云 OSS | 阿里云 OSS | 无变化 |

---

## 3. 架构变更总览

### 3.1 认证架构对比

```
┌─────────────────────────────────────────────────────────────────┐
│                          v1 认证架构                             │
├─────────────────────────────────────────────────────────────────┤
│  前端请求 → WebMvcConfiguration → JwtTokenAdminInterceptor      │
│                                      ↓                          │
│                              TokenService                       │
│                                      ↓                          │
│                              Redis 验证 Token                   │
│                                      ↓                          │
│                              BaseContext 存储用户信息            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                          v2 认证架构                             │
├─────────────────────────────────────────────────────────────────┤
│  前端请求 → Spring Security Filter Chain                        │
│                ↓                                                │
│         AuthorizationServerConfig (@Order(1))                   │
│          处理 /oauth2/** 端点                                    │
│                ↓                                                │
│         ResourceServerConfig (@Order(2))                        │
│          处理其他 API，验证 JWT                                   │
│                ↓                                                │
│         @PreAuthorize 注解进行方法级权限控制                      │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 用户模型对比

```
┌─────────────────────────────────────────────────────────────────┐
│                          v1 用户模型                             │
├─────────────────────────────────────────────────────────────────┤
│  Admin 表                                                       │
│  ├── id, username, password, salt                               │
│  ├── nickname, email                                            │
│  └── role (1=管理员, 0=游客)                                     │
│                                                                  │
│  角色: 管理员 / 游客（二元模型）                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                          v2 用户模型                             │
├─────────────────────────────────────────────────────────────────┤
│  SysUser 表                                                     │
│  ├── id, username, password (BCrypt)                            │
│  ├── nickname, email, avatar                                    │
│  ├── userType (1=博客用户, 2=管理员, 3=后台游客)                 │
│  ├── status (1=启用, 0=禁用)                                    │
│  ├── loginType (1=本地, 2=GitHub, 3=Gitee)                      │
│  ├── oauthId, oauthProvider                                     │
│  └── lastLoginTime, lastLoginIp                                 │
│                                                                  │
│  SysRole 表 (角色: ADMIN/USER/VISITOR/AUTHOR/AUDITOR)           │
│  SysPermission 表 (权限: article:write, system:config 等)       │
│  SysUserRole 表 (用户-角色关联)                                  │
│  SysRolePermission 表 (角色-权限关联)                            │
│                                                                  │
│  角色: RBAC 四角色模型                                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 认证授权体系变化（重点）

### 4.1 v1 认证方式

**登录接口**：`POST /admin/admin/login`

**请求参数**：
```json
{
  "username": "admin",
  "password": "123456",
  "code": "123456"
}
```

**响应**：
```json
{
  "code": 1,
  "msg": null,
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

**Token 管理**：
- Token 存储在 Redis 中
- 前端在请求头中携带 `Authorization: <token>`
- Token 有效期通过配置文件设置

### 4.2 v2 认证方式

**登录接口**：`POST /oauth2/token`

**管理端登录请求**（grant_type: admin_password_code）：
```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=admin_password_code
&username=admin
&password=123456
&code=123456
```

**博客端邮箱登录请求**（grant_type: email_code）：
```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=email_code
&email=user@example.com
&code=123456
```

**响应**：
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 172800,
  "scope": "openid profile"
}
```

**Token 管理**：
- JWT 自包含用户信息（roles, user_id, nickname）
- Token 通过 HttpOnly Cookie 传递（Cookie 名称: `dL9xK2mP5vQ8`）
- 支持 Refresh Token 刷新
- 支持 Token 吊销（`POST /oauth2/revoke`）

### 4.3 v2 新增的 OAuth2 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/oauth2/token` | POST | 获取 Token（支持多种 grant_type） |
| `/oauth2/authorize` | GET/POST | OAuth2 授权端点 |
| `/oauth2/jwks` | GET | JWT 公钥端点 |
| `/oauth2/revoke` | POST | 吊销 Token |
| `/oauth2/introspect` | POST | Token 内省 |
| `/login/oauth2/code/{registrationId}` | GET | 第三方 OAuth2 回调 |

### 4.4 自定义 Grant Type

#### admin_password_code（管理端登录）
- 用途：管理员通过用户名 + 密码 + 验证码登录
- 参数：`username`, `password`, `code`
- 验证：密码校验 + 验证码校验 + 登录锁定检查

#### email_code（博客端登录）
- 用途：访客通过邮箱 + 验证码登录/注册
- 参数：`email`, `code`
- 验证：验证码校验，不存在则自动注册

### 4.5 第三方 OAuth2 登录

**支持的平台**：
- GitHub
- Gitee

**登录流程**：
1. 前端跳转：`/oauth2/authorization/{registrationId}`（如 `/oauth2/authorization/github`）
2. 用户在第三方平台授权
3. 回调到：`/login/oauth2/code/{registrationId}`
4. 后端处理后重定向到前端首页

**配置**（application-dev.yml）：
```yaml
dl:
  oauth2:
    github:
      client-id: "your-github-client-id"
      client-secret: "your-github-client-secret"
    gitee:
      client-id: "your-gitee-client-id"
      client-secret: "your-gitee-client-secret"
  oauth2-redirect:
    success-url: http://localhost:5173/
    failure-url: http://localhost:5173/login
```

---

## 5. API 接口变化

### 5.1 移除的接口（v1 有，v2 无）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/admin/admin/login` | POST | 管理员登录（改为 OAuth2） |
| `/admin/admin/logout` | POST | 管理员登出（改为 OAuth2） |
| `/admin/admin/sendCode` | POST | 发送验证码（改为 `/blog/auth/sendCode`） |
| `/admin/admin/getAdmin` | GET | 获取管理员信息（改为 OAuth2 Token） |
| `/admin/admin/editPassword` | PUT | 修改密码（改为 `/blog/auth/*`） |
| `/admin/admin/editNickname` | PUT | 修改昵称（改为 `/blog/auth/*`） |
| `/admin/admin/editEmail` | PUT | 修改邮箱（改为 `/blog/auth/*`） |

### 5.2 新增的接口（v1 无，v2 有）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/oauth2/token` | POST | 获取 Token |
| `/oauth2/authorize` | GET/POST | OAuth2 授权 |
| `/oauth2/jwks` | GET | JWT 公钥 |
| `/oauth2/revoke` | POST | 吊销 Token |
| `/oauth2/introspect` | POST | Token 内省 |
| `/login/oauth2/code/{id}` | GET | OAuth2 回调 |
| `/blog/auth/register` | POST | 用户注册 |
| `/blog/auth/sendCode` | POST | 发送验证码 |
| `/oauth2/authorization/github` | GET | GitHub 登录 |
| `/oauth2/authorization/gitee` | GET | Gitee 登录 |

### 5.3 接口路径变化

| v1 路径 | v2 路径 | 变化说明 |
|---------|---------|----------|
| `/admin/admin/*` | `/oauth2/*` | 登录相关接口迁移到 OAuth2 |
| 其他 `/admin/*` | `/admin/*` | 后台管理接口路径不变 |
| `/blog/*` | `/blog/*` | 博客前台接口路径不变 |
| `/cv/*` | `/cv/*` | 简历接口路径不变 |
| `/home/*` | `/home/*` | 首页接口路径不变 |

### 5.4 接口权限控制变化

**v1 权限控制**：
- 基于拦截器的路径匹配
- `/admin/**` 路径需要登录
- 无细粒度权限控制

**v2 权限控制**：
- 基于 `@PreAuthorize` 注解的方法级权限
- 支持四种角色：`ADMIN`、`AUTHOR`、`AUDITOR`、`GUEST`
- 权限粒度：`article:write`、`system:config` 等

**示例**：
```java
// v2 后台文章控制器
@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
@PostMapping
public Result saveArticle(@RequestBody ArticleDTO dto) {
    // ...
}

@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public Result deleteArticle(@PathVariable Long id) {
    // ...
}
```

---

## 6. 数据库表结构变化

### 6.1 移除的表

| 表名 | 说明 |
|------|------|
| `admin` | 管理员表（迁移到 `sys_user`） |

### 6.2 新增的表

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户表（替代 `admin`） |
| `sys_role` | 角色表 |
| `sys_permission` | 权限表 |
| `sys_user_role` | 用户-角色关联表 |
| `sys_role_permission` | 角色-权限关联表 |
| `sys_login_lock` | 登录锁定表 |
| `oauth2_registered_client` | OAuth2 客户端注册表 |
| `oauth2_authorization` | OAuth2 授权记录表 |
| `oauth2_authorization_consent` | OAuth2 授权同意表 |
| `article_authors` | 文章作者表 |
| `article_audit_logs` | 文章审核日志表 |

### 6.3 sys_user 表结构

```sql
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    email VARCHAR(100),
    avatar VARCHAR(255),
    user_type TINYINT NOT NULL DEFAULT 1 COMMENT '1博客用户 2管理员 3后台游客',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    login_type TINYINT DEFAULT 1 COMMENT '1本地 2GitHub 3Gitee',
    oauth_id VARCHAR(100),
    oauth_provider VARCHAR(20),
    last_login_time DATETIME,
    last_login_ip VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 6.4 sys_role 表结构

```sql
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    role_key VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识',
    description VARCHAR(100),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 预置角色
INSERT INTO sys_role (role_name, role_key, description) VALUES
('管理员', 'ADMIN', '系统管理员，拥有所有权限'),
('普通用户', 'USER', '普通用户，基础权限'),
('访客', 'VISITOR', '访客，只读权限'),
('作者', 'AUTHOR', '内容作者，可发布文章'),
('审核员', 'AUDITOR', '内容审核员，可审核文章');
```

### 6.5 sys_permission 表结构

```sql
CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    permission_key VARCHAR(100) NOT NULL UNIQUE COMMENT '权限标识',
    description VARCHAR(100),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 预置权限
INSERT INTO sys_permission (permission_name, permission_key, description) VALUES
('文章管理', 'article:write', '创建、编辑、删除文章'),
('文章审核', 'article:audit', '审核文章发布'),
('系统配置', 'system:config', '修改系统配置'),
('用户管理', 'user:manage', '管理用户账号');
```

### 6.6 其他表变化

| 表名 | 变化 |
|------|------|
| `article_comments` | 移除匿名字段，要求必须登录 |
| `article_likes` | 移除匿名字段，要求必须登录 |
| `messages` | 移除匿名字段，要求必须登录 |
| `rss_subscriptions` | 移除匿名字段，要求必须登录 |
| 所有表 | 主键改为 BIGINT |

---

## 7. 前端登录流程改造

### 7.1 v1 登录流程

```javascript
// v1 登录
async function login(username, password, code) {
  const response = await fetch('/admin/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, code })
  });
  
  const result = await response.json();
  if (result.code === 1) {
    // 存储 Token
    localStorage.setItem('token', result.data.token);
    // 存储用户信息
    localStorage.setItem('userInfo', JSON.stringify(result.data));
  }
}

// v1 请求拦截器
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = token;
  }
  return config;
});
```

### 7.2 v2 登录流程

```javascript
// v2 管理端登录（grant_type: admin_password_code）
async function adminLogin(username, password, code) {
  const params = new URLSearchParams();
  params.append('grant_type', 'admin_password_code');
  params.append('username', username);
  params.append('password', password);
  params.append('code', code);

  const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params,
    credentials: 'include' // 重要：允许 Cookie
  });
  
  const result = await response.json();
  if (result.access_token) {
    // Token 已通过 HttpOnly Cookie 设置，无需手动存储
    // 获取用户信息
    await getUserInfo();
  }
}

// v2 博客端邮箱登录（grant_type: email_code）
async function emailLogin(email, code) {
  const params = new URLSearchParams();
  params.append('grant_type', 'email_code');
  params.append('email', email);
  params.append('code', code);

  const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params,
    credentials: 'include'
  });
  
  const result = await response.json();
  if (result.access_token) {
    await getUserInfo();
  }
}

// v2 请求拦截器（Cookie 模式，无需手动添加 Token）
axios.interceptors.request.use(config => {
  config.withCredentials = true; // 携带 Cookie
  return config;
});

// v2 错误响应拦截器（处理 401）
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // Token 过期或无效，跳转登录页
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

### 7.3 v2 发送验证码

```javascript
// 发送邮箱验证码
async function sendVerificationCode(email) {
  const response = await fetch('/blog/auth/sendCode', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  
  return await response.json();
}
```

### 7.4 v2 用户注册

```javascript
// 用户注册
async function register(email, code) {
  const response = await fetch('/blog/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code })
  });
  
  return await response.json();
}
```

### 7.5 v2 第三方登录

```javascript
// GitHub 登录（跳转）
function githubLogin() {
  window.location.href = '/oauth2/authorization/github';
}

// Gitee 登录（跳转）
function giteeLogin() {
  window.location.href = '/oauth2/authorization/gitee';
}
```

### 7.6 v2 Token 刷新

```javascript
// 使用 Refresh Token 刷新 Access Token
async function refreshToken() {
  const params = new URLSearchParams();
  params.append('grant_type', 'refresh_token');
  params.append('refresh_token', getRefreshTokenFromCookie());

  const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params,
    credentials: 'include'
  });
  
  return await response.json();
}
```

### 7.7 v2 登出

```javascript
// 登出
async function logout() {
  await fetch('/oauth2/revoke', {
    method: 'POST',
    credentials: 'include'
  });
  
  // 清除本地状态
  localStorage.removeItem('userInfo');
  window.location.href = '/login';
}
```

---

## 8. 前端权限控制改造

### 8.1 v1 权限模型

```javascript
// v1 简单的角色判断
const isAdmin = userInfo.role === 1;
const isGuest = userInfo.role === 0;

// 前端路由守卫
router.beforeEach((to, from, next) => {
  if (to.path.startsWith('/admin') && !isAdmin) {
    next('/login');
  } else {
    next();
  }
});
```

### 8.2 v2 权限模型

```javascript
// v2 RBAC 角色判断
const roles = userInfo.roles || []; // ['ADMIN', 'USER', 'AUTHOR']
const hasRole = (role) => roles.includes(role);
const hasAnyRole = (...roleList) => roleList.some(r => roles.includes(r));

// 权限判断
const permissions = userInfo.permissions || []; // ['article:write', 'system:config']
const hasPermission = (perm) => permissions.includes(perm);

// 前端路由守卫
router.beforeEach((to, from, next) => {
  const requiredRoles = to.meta.roles || [];
  
  if (requiredRoles.length > 0 && !hasAnyRole(...requiredRoles)) {
    next('/403'); // 无权限页面
  } else {
    next();
  }
});

// 路由配置示例
const routes = [
  {
    path: '/admin/articles',
    component: ArticleManagement,
    meta: { roles: ['ADMIN', 'AUTHOR'] }
  },
  {
    path: '/admin/settings',
    component: SystemSettings,
    meta: { roles: ['ADMIN'] }
  }
];
```

### 8.3 v2 角色权限对照表

| 角色 | 标识 | 权限说明 |
|------|------|----------|
| 管理员 | `ADMIN` | 拥有所有权限 |
| 作者 | `AUTHOR` | 可创建、编辑自己的文章 |
| 审核员 | `AUDITOR` | 可审核文章发布 |
| 普通用户 | `USER` | 基础权限（评论、点赞等） |
| 访客 | `VISITOR` | 只读权限 |

### 8.4 v2 前端权限指令（可选）

```javascript
// 自定义权限指令
Vue.directive('permission', {
  mounted(el, binding) {
    const { value } = binding;
    const roles = store.state.user.roles;
    
    if (value && value instanceof Array) {
      const hasPermission = value.some(role => roles.includes(role));
      if (!hasPermission) {
        el.parentNode?.removeChild(el);
      }
    }
  }
});

// 使用示例
// <button v-permission="['ADMIN', 'AUTHOR']">发布文章</button>
// <button v-permission="['ADMIN']">删除文章</button>
```

---

## 9. 新增功能模块

### 9.1 第三方 OAuth2 登录

**前端集成**：
```vue
<template>
  <div class="login-page">
    <h2>登录</h2>
    
    <!-- 本地登录表单 -->
    <form @submit.prevent="localLogin">
      <input v-model="email" placeholder="邮箱" />
      <input v-model="code" placeholder="验证码" />
      <button @click="sendCode">发送验证码</button>
      <button type="submit">登录</button>
    </form>
    
    <!-- 第三方登录 -->
    <div class="oauth-login">
      <button @click="githubLogin">
        <img src="/github-icon.svg" /> GitHub 登录
      </button>
      <button @click="giteeLogin">
        <img src="/gitee-icon.svg" /> Gitee 登录
      </button>
    </div>
  </div>
</template>

<script>
export default {
  methods: {
    githubLogin() {
      window.location.href = '/oauth2/authorization/github';
    },
    giteeLogin() {
      window.location.href = '/oauth2/authorization/gitee';
    }
  }
}
</script>
```

### 9.2 用户注册

```vue
<template>
  <div class="register-page">
    <h2>注册</h2>
    <form @submit.prevent="register">
      <input v-model="email" placeholder="邮箱" />
      <input v-model="code" placeholder="验证码" />
      <button @click="sendCode">发送验证码</button>
      <button type="submit">注册</button>
    </form>
  </div>
</template>

<script>
export default {
  methods: {
    async sendCode() {
      await fetch('/blog/auth/sendCode', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: this.email })
      });
    },
    async register() {
      await fetch('/blog/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: this.email, code: this.code })
      });
    }
  }
}
</script>
```

### 9.3 文章审核流程

v2 新增了文章审核功能，支持以下角色：
- **AUTHOR**：创建、编辑自己的文章
- **AUDITOR**：审核文章发布
- **ADMIN**：拥有所有权限

```javascript
// 文章状态流转
// DRAFT → PENDING → APPROVED → PUBLISHED
//                  → REJECTED

// 提交审核
async function submitForReview(articleId) {
  await fetch(`/admin/articles/${articleId}/submit`, {
    method: 'POST',
    credentials: 'include'
  });
}

// 审核通过
async function approveArticle(articleId) {
  await fetch(`/admin/articles/${articleId}/approve`, {
    method: 'POST',
    credentials: 'include'
  });
}

// 审核拒绝
async function rejectArticle(articleId, reason) {
  await fetch(`/admin/articles/${articleId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
    credentials: 'include'
  });
}
```

---

## 10. 配置文件变化

### 10.1 新增配置项

```yaml
# application.yml 新增
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:5922  # JWT 签发者 URI

dl:
  # OAuth2 配置
  oauth2:
    github:
      client-id: ""
      client-secret: ""
    gitee:
      client-id: ""
      client-secret: ""
  oauth2-redirect:
    success-url: http://localhost:5173/
    failure-url: http://localhost:5173/login
```

### 10.2 移除的配置项

```yaml
# v1 有，v2 无
dl:
  admin:
    default-password: 123456  # 移除，默认密码逻辑改变
```

### 10.3 完整配置示例

```yaml
# application-dev.yml 完整配置
dl:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    host: localhost
    port: 3306
    database: DriftingLeaves
    username: root
    password: 1234
  redis:
    host: localhost
    port: 6379
    password: 123456
    database: 0
  jwt:
    ttl: 172800000  # 2天
    cookie-name: dL9xK2mP5vQ8
  security:
    dev-code: 123456  # 开发环境固定验证码
  oauth2:
    github:
      client-id: ""
      client-secret: ""
    gitee:
      client-id: ""
      client-secret: ""
  oauth2-redirect:
    success-url: http://localhost:5173/
    failure-url: http://localhost:5173/login
```

---

## 11. 常见问题 FAQ

### Q1: v2 的 Token 存储在哪里？

**A**: v2 使用 HttpOnly Cookie 存储 Token，Cookie 名称为 `dL9xK2mP5vQ8`。前端无需手动存储 Token，浏览器会自动携带 Cookie。

### Q2: 如何判断用户是否登录？

**A**: 
```javascript
// 方式1：检查响应状态码
try {
  const response = await fetch('/api/some-endpoint', {
    credentials: 'include'
  });
  if (response.status === 401) {
    // 未登录
  }
} catch (error) {
  // 处理错误
}

// 方式2：调用获取用户信息接口
async function checkLogin() {
  try {
    const response = await fetch('/blog/auth/me', {
      credentials: 'include'
    });
    return response.ok;
  } catch {
    return false;
  }
}
```

### Q3: 如何获取用户角色和权限？

**A**: 
```javascript
// 解析 JWT Token 中的信息
async function getUserInfo() {
  const response = await fetch('/blog/auth/me', {
    credentials: 'include'
  });
  const userInfo = await response.json();
  // userInfo 包含 roles 和 permissions
  return userInfo;
}
```

### Q4: Token 过期后如何处理？

**A**: 
```javascript
// 使用响应拦截器处理 401
axios.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      // 尝试使用 Refresh Token 刷新
      try {
        await refreshToken();
        // 重新发送原请求
        return axios(error.config);
      } catch {
        // 刷新失败，跳转登录页
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
```

### Q5: 第三方登录回调后如何获取用户信息？

**A**: 第三方登录成功后，后端会重定向到前端首页。前端可以通过调用 `/blog/auth/me` 接口获取用户信息。

### Q6: 如何处理 CORS 问题？

**A**: v2 使用 Cookie 模式，需要配置 CORS 允许携带凭证：
```javascript
// axios 配置
axios.defaults.withCredentials = true;

// 后端 CORS 配置
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
```

### Q7: 如何区分管理端和博客端用户？

**A**: 
```javascript
// 通过 user_type 字段区分
const userType = userInfo.userType;
// 1 = 博客用户
// 2 = 管理员
// 3 = 后台游客

// 或通过角色区分
const roles = userInfo.roles;
const isAdmin = roles.includes('ADMIN');
const isAuthor = roles.includes('AUTHOR');
```

---

## 附录：迁移检查清单

### 前端改造清单

- [ ] 登录接口从 `/admin/admin/login` 改为 `/oauth2/token`
- [ ] 登录请求格式从 JSON 改为 form-urlencoded
- [ ] Token 存储从 localStorage 改为 HttpOnly Cookie
- [ ] 请求拦截器添加 `credentials: 'include'`
- [ ] 移除手动添加 Authorization Header 的代码
- [ ] 添加 401 响应拦截器
- [ ] 添加第三方登录入口（GitHub/Gitee）
- [ ] 添加用户注册页面
- [ ] 路由守卫改为基于角色的权限控制
- [ ] 更新权限判断逻辑（从 role 数字改为 roles 数组）
- [ ] 测试登出功能（调用 `/oauth2/revoke`）

### 后端改造清单

- [ ] 移除 `JwtTokenAdminInterceptor`
- [ ] 移除 `TokenService`
- [ ] 移除 `EncryptPasswordService`
- [ ] 移除 `Admin` 实体和相关 Mapper
- [ ] 添加 OAuth2 授权服务器配置
- [ ] 添加 RBAC 相关实体和 Mapper
- [ ] 添加第三方 OAuth2 登录配置
- [ ] 更新所有 Controller 的权限注解
- [ ] 更新数据库表结构

---

## 联系方式

如有问题，请通过以下方式联系：
- GitHub Issues: [mikubob/DriftingLeaves-v2](https://github.com/mikubob/DriftingLeaves-v2/issues)
