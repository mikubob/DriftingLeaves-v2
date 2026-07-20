# 后端改造方案 & 前端对齐清单

> 针对前端《DriftingLeaves v2 后端待改清单》(2026-07-20)的核查与回应文档。
>
> - **后端仓库**:`DL-server` 模块
> - **核查日期**:2026-07-20
> - **目标**:让前端 Admin/Blog 两端能够完整对接 OAuth2 + JWT + RBAC 体系
> - **本文档状态**:决策已对齐 → 后端进入实施(2026-07-20)

---

## 一、核查结论总览

后端已逐项核查前端提出的 14 个问题,结论如下:

| # | 前端提出的问题 | 后端核查结论 | 后端处理态度 |
|---|---|---|---|
| 1 | 管理端发验证码接口缺失 | ✅ **确认缺失**。admin 包 19 个 Controller 无一涉及 auth | **本期处理(P0)** |
| 2 | `/admin/me`、`/blog/me` 缺失 | ✅ **确认缺失**。全局搜索零命中 | **本期处理(P0)** |
| 3 | 管理端个人设置接口缺失 | ✅ **确认缺失**。仅 `PersonalInfoController`(站点信息),无 `/admin/me` PUT | **本期处理(P1)** |
| 4 | OAuth2 登录缺 refresh_token | ✅ **确认缺失**。Handler 注释明确说不颁发 | **本期处理(P1)** |
| 5 | `/oauth2/revoke` 不清 Cookie | ✅ **确认缺失**。无自定义 revocation 处理器 | **本期处理(P0)** |
| 6 | OAuth2 重定向 URL 按环境配置 | ⚠️ dev 已配,prod yml 完全缺失该配置块 | **本期处理(P1)** |
| 7 | 注册接口二次校验 | ✅ **基本完整**。唯一性、锁定、验证码都已校验 | **不改,仅说明** |
| 8 | `/admin/admin/*` 旧接口残留 | ❌ **不存在残留**。旧接口已彻底清理 | **无需处理** |
| 9 | 角色返回格式约定 | ⚠️ 未约定(JWT 带前缀,`/me` 尚不存在) | **本期约定(P1)** |
| 10 | 博客端评论/点赞/留言鉴权 | ✅ 已强制 `hasRole("GUEST")` | **待产品决策** |
| 11 | `PersonalInfoController` 权限 | ⚠️ 设计定位混淆(站点信息 vs 用户资料) | **待前端确认定位** |
| 12 | `/oauth2/revoke` 自定义处理器 | ✅ **确认缺失**(同 #5) | **本期处理(P0)** |
| 13 | dev-code 配置 | ✅ dev 已配 `123456` | **无需处理** |
| 14 | 用户管理 CRUD | ✅ **确认缺失**。无 `SysUserController` | **本期跳过(P2)** |

## 二、核查中额外发现的前置问题(必须先处理)

下列问题不在前端清单中,但会同时阻塞多个 P0 项,**必须作为前置基础改造先做**。

### 前置 A:`SecurityUser` 缺字段

当前 `SecurityUser` 只包含 `userId / username / password / userType / nickname / authorities`,**缺 `email`、`avatar`**。

- 影响:`/me` 接口无法返回 email/avatar;OAuth2 登录 JWT claims 也拿不到这两个字段
- 处理:扩展 `SecurityUser` 字段,同步更新 `UserDetailsServiceImpl` 构造调用

### 前置 B:Cookie 工具逻辑重复且无统一常量

- `access_token` Cookie 名称常量在 **3 处重复定义**(`OAuth2LoginSuccessHandler`、`OAuth2TokenResponseCookieHandler`、`CookieBearerTokenResolver`)
- Cookie 写入逻辑在两处**几乎复制粘贴**
- `refresh_token` 常量仅在 1 处定义
- 处理:抽取 `CookieConstant` + `CookieUtils`,后续 revoke 清 Cookie、OAuth2 写 refresh_token 全部复用

### 前置 C:JWT claims 缺 `email`、`avatar`

`JwtCustomizerConfig` 与 `OAuth2LoginSuccessHandler` 当前只塞 `roles`、`user_id`、`nickname`,**缺 `email`、`avatar`**。

- 影响:前端即使解析 JWT 也拿不到 email/avatar
- 处理:依赖前置 A,在两处 claims 构造中补字段

### 前置 D(重要告警):博客端 `@AuthenticationPrincipal SecurityUser` 注入可能为 null

`ResourceServerConfig.jwtAuthenticationConverter()` 把 principal 设成 `Jwt` 对象本身(仅 `setPrincipalClaimName("sub")` 改了 `getName()` 返回值),**未注册 `Converter<Jwt, SecurityUser>`**。

- 现状:博客端 6 个 Controller 共 13 处用 `@AuthenticationPrincipal SecurityUser securityUser` 注入,**实际运行中会拿到 null**
- 目前能跑:GET 接口都做了 `securityUser != null` 兜底
- 处理:新增 `/blog/me` 等接口时**改用 `@AuthenticationPrincipal Jwt jwt`**,然后 `jwt.getClaim("user_id")` 取值
- 后续会评估是否注册 `Converter<Jwt, SecurityUser>` 一次性解决所有注入问题

### 前置 E:`OAuth2AuthorizationMapper` 是装饰性代码

定义了但**无任何业务代码调用**,`JdbcOAuth2AuthorizationService` 直接用 `JdbcTemplate` 绕过它。本期改造若需手动操作 `oauth2_authorization` 表,统一走 `OAuth2AuthorizationService` API,不动 Mapper。

---

## 三、后端采用的方案

### 阶段 1:前置基础改造

| 项 | 文件 | 改动 |
|---|---|---|
| 前置 A | `SecurityUser.java`、`UserDetailsServiceImpl.java` | 加 `email`、`avatar` 字段 |
| 前置 B | 新增 `CookieConstant`、`CookieUtils` | 统一 Cookie 读写 |
| 前置 C | `JwtCustomizerConfig.java`、`OAuth2LoginSuccessHandler.java` | claims 补 `email`、`avatar` |

### 阶段 2:P0 必做项

#### P0-1. 管理端发验证码接口(#1)

**新增接口**:

```http
POST /admin/auth/sendCode
Content-Type: application/json

{ "email": "admin@example.com" }
```

**响应**:`Result.success()`(无 data)

**实现要点**:
- 通过 `SysUserMapper.selectByEmail(email)` 找到 `sys_user`
- 调用现有 `VerifyCodeService`(按 `userId` 存)生成并保存验证码
- 异步发送邮件(`AsyncEmailService`)
- 与 `AdminPasswordCodeAuthenticationProvider` 完全对齐(它也是按 userId 校验)
- 60s 频率限制、5 分钟 TTL、5 次错误锁定 30 分钟(均已存在于 `VerifyCodeService`)

**权限**:`/admin/auth/**` 设为 `permitAll`(未登录状态调用)

**安全策略**:对不存在的邮箱**仍返回 success**(避免账号嗅探),仅记日志。**请前端确认是否接受此策略**。

#### P0-2. `/admin/me` 与 `/blog/me` 接口(#2、#9)

**共用响应 VO**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "管理员",
    "email": "admin@example.com",
    "avatar": "https://xxx/avatar.png",
    "roles": ["ADMIN", "AUTHOR"]
  }
}
```

**角色格式约定(#9)**:`roles` 返回**不带 `ROLE_` 前缀**的角色名(如 `["ADMIN", "AUTHOR"]`),前端可直接用于路由守卫与菜单过滤,无需二次处理。

**Admin 端**:

```http
GET /admin/me
```

- 权限:`hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')`(自动覆盖,无需改 `ResourceServerConfig`)
- 实现:从 JWT claim `user_id` 取 userId,查 `sys_user` 表组装 VO

**Blog 端**:

```http
GET /blog/auth/me
```

- 权限:`hasRole('GUEST')`
- ⚠️ **路径特例**:`/blog/auth/**` 当前是 permitAll,需在 `ResourceServerConfig` 中将 `GET /blog/auth/me` 特例放在 permitAll 规则**之前**:

```java
.requestMatchers(HttpMethod.GET, "/blog/auth/me").hasRole("GUEST")
.requestMatchers("/blog/auth/**").permitAll()
```

- 实现:使用 `@AuthenticationPrincipal Jwt jwt`(避免前置 D 的注入陷阱)

#### P0-3. `/oauth2/revoke` 清 Cookie(#5、#12)

**待前端选定方案**(见第四节决策项):

- **方案 A(后端推荐)**:新增包装接口 `POST /api/logout`
  - 后端从 Cookie 读 access_token / refresh_token
  - 调用 `OAuth2AuthorizationService.remove()` 失效 token
  - 清除浏览器 Cookie
  - 前端只需 `fetch('/api/logout')`,无需传 token
- **方案 B**:仅自定义 SAS 默认 `/oauth2/revoke` 的响应处理器
  - 前端需自行从 Cookie 读 token 后,以 form-urlencoded 形式调 `/oauth2/revoke?token=xxx`
  - 复杂度更高,跨域场景可能更麻烦

### 阶段 3:P1 项

#### P1-1. 管理端个人设置接口(#3)

**新增接口**:

```http
PUT /admin/me
Content-Type: application/json

{
  "nickname": "新昵称",
  "email": "new@example.com",
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```

**字段全部可选**(只传需要改的)。

**响应**:`Result.success()`(无 data)

**业务逻辑**:
1. 仅允许修改当前登录用户自己的信息(`jwt.getClaim("user_id")` 校验)
2. 修改密码:必须校验 `oldPassword` → BCrypt 匹配 → 加密新密码入库
3. 修改邮箱:校验新邮箱唯一性(`sys_user.email` 已有 UNIQUE KEY)→ 入库
4. 修改昵称:直接入库
5. **修改密码或邮箱后,建议前端主动重新登录获取新 token**(因为 JWT 不可变,旧 token 中的 nickname/email 会过期)

**权限**:`hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')`

**实现**:新建 `ISysUserService` + `SysUserServiceImpl`(当前项目缺此 Service)

#### P1-2. OAuth2 登录补 refresh_token(#4)

**改动文件**:`OAuth2LoginSuccessHandler.java`

**方案**:通过 `OAuth2AuthorizationService.save()` 持久化授权记录,生成 refresh_token JWT(7 天有效),写入 `refresh_token` Cookie。

**Refresh Token 接口**(沿用 SAS 标准):

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token=<refresh_token_value>
&client_id=<client_id>
```

**Refresh Token Cookie**:
- Name: `refresh_token`
- Path: `/`
- HttpOnly: 启用
- SameSite: `Strict`(或 `Lax`,见风险点)
- MaxAge: 7 天

**前端使用方式**:
- access_token 过期(401)时,前端自动调 `/oauth2/token` 用 refresh_token 换新 access_token
- 后端响应自动写回新 Cookie

#### P1-3. OAuth2 重定向 URL 按环境配置(#6)

**改动文件**:`application-prod.yml`

**补充配置**:

```yaml
dl:
  oauth2-redirect:
    success-url: https://your-domain.com/
    failure-url: https://your-domain.com/login
  oauth2:
    github:
      client-id: ${GITHUB_CLIENT_ID}
      client-secret: ${GITHUB_CLIENT_SECRET}
    gitee:
      client-id: ${GITEE_CLIENT_ID}
      client-secret: ${GITEE_CLIENT_SECRET}
```

**多前端源回跳**(可选):支持 Admin/Blog 端跳不同地址,通过 OAuth2 `state` 参数传递原始 referer。**待前端确认是否本期实现**。

### 阶段 4:P2 项(本期跳过)

- **#14 用户管理 CRUD**:本期跳过,后续按需补充

---

## 四、决策对齐结果(前端已确认)

| # | 决策项 | 前端选择 | 备注 |
|---|---|---|---|
| 1 | `/admin/auth/sendCode` 对不存在邮箱的处理 | **A** | 不存在也返回 success,仅记日志,防账号嗅探 |
| 2 | `/oauth2/revoke` 清 Cookie 方案 | **A** | 新增 `POST /api/logout`,Admin/Blog 共用 |
| 3 | OAuth2 refresh_token 本期是否处理 | **A** | 本期完整实现 |
| 4 | OAuth2 多前端源回跳 | **A** | 通过 `state` 传递 redirect_uri,支持 Admin/Blog 跳不同地址 |
| 5 | 博客端评论/点赞/留言匿名能力 | **A** | 强制 GUEST 登录 |
| 6 | `PersonalInfoController` 定位 | **A** | 站点公开信息,保持当前权限;用户资料走 `/admin/me` |
| 7 | Cookie SameSite 策略 | **B** | 改为 `Lax`,保证 OAuth2 回调顺滑 |

### 细节补充

#### 1. `/api/logout` 鉴权与跨端共用

**结论**:Admin 端和 Blog 端**共用同一个 `/api/logout`**,不区分前后端。

**鉴权策略**:
- **不强制要求有效 access_token**
- 即使 Cookie 中 token 已过期或不存在,也允许调用(否则前端遇到 token 过期时无法登出)
- 后端逻辑:
  1. 从 Cookie 读 access_token / refresh_token(可能为空)
  2. 若存在,调用 `OAuth2AuthorizationService.remove()` 失效
  3. 无论 token 是否有效,都清空 Cookie
  4. 返回 success

**权限**:`permitAll`

#### 2. `/blog/auth/me` 的 GUEST 角色确认

**结论**:博客端注册用户**默认且仅有 GUEST 角色**。

**证据**:[BlogUserServiceImpl.java](file:///d:/CodingFiles/Blog/DriftingLeaves-v2/DL-server/src/main/java/com/xuan/service/impl/BlogUserServiceImpl.java) 第 142-153 行,注册成功后仅关联 `GUEST` 角色:

```java
// 6. 关联 GUEST 角色
Long guestRoleId = sysUserRoleMapper.selectRoleIdByCode("GUEST");
// ...
SysUserRole userRole = SysUserRole.builder()
        .userId(sysUser.getId())
        .roleId(guestRoleId)
        .build();
sysUserRoleMapper.insert(userRole);
```

**前端可放心使用**:
- 所有博客端注册用户角色均为 `["GUEST"]`
- 没有 VIP / 普通用户 等分级
- `hasRole("GUEST")` 等价于"已登录的博客用户"

#### 3. OAuth2 `state` 参数传递 redirect_uri 的实现约定

**前端发起登录时**:

```http
GET /oauth2/authorization/github?redirect_uri=http://localhost:5174/#/login
```

或放到 `state` 参数中:

```http
GET /oauth2/authorization/github?state=<base64(redirect_uri)>
```

**后端处理**:
- `OAuth2LoginSuccessHandler` 优先使用 `state` / `redirect_uri` 参数中的地址
- 若参数缺失,fallback 到 `dl.oauth2-redirect.success-url`
- 安全校验:redirect_uri 必须在配置的白名单内(防开放重定向)

**待后端实施时确认最终传参方式**(`redirect_uri` query 参数 vs `state` 编码)。

---

## 五、实施顺序与依赖关系

```
阶段 1:前置基础改造(必做,阻塞 P0)
  ├─ 前置 A:SecurityUser 加字段
  ├─ 前置 B:CookieUtils 抽取
  └─ 前置 C:JWT claims 补字段

阶段 2:P0 项
  ├─ P0-1:管理端发验证码接口
  ├─ P0-2:/admin/me + /blog/auth/me
  └─ P0-3:/oauth2/revoke 清 Cookie(依赖决策 2)

阶段 3:P1 项
  ├─ P1-1:/admin/me PUT 个人设置
  ├─ P1-2:OAuth2 refresh_token(依赖决策 3)
  └─ P1-3:prod 配置 + 多前端源回跳(依赖决策 4)

阶段 4:P2 项(本期跳过)
  └─ 用户管理 CRUD
```

---

## 六、风险与回归点

1. **`SecurityUser` 加字段**会破坏所有 `new SecurityUser(...)` 调用,需全项目 grep 更新
2. **`/blog/auth/me` 权限特例**必须放在 `/blog/auth/**` permitAll 之前,否则被通配规则先匹配
3. **`@AuthenticationPrincipal SecurityUser` 在 blog 端拿不到值**(前置 D),所有 blog 端涉及当前用户的接口都应改用 `@AuthenticationPrincipal Jwt`
4. **`oauth2_authorization` 表**必须存在于数据库(P1-2 依赖)
5. **Cookie SameSite=Strict** 在 OAuth2 跨站回调时可能丢失 Cookie(决策 7)
6. **`/admin/me` PUT 修改密码/邮箱后**,JWT 中的旧字段会过期,需前端主动重新登录

---

## 七、接口契约速查表

| 接口 | 方法 | 路径 | 鉴权 | 用途 |
|---|---|---|---|---|
| 发送管理端验证码 | POST | `/admin/auth/sendCode` | permitAll | 管理端登录前发验证码 |
| 获取当前用户(Admin) | GET | `/admin/me` | ADMIN/AUTHOR/AUDITOR | 获取当前登录用户信息 |
| 修改当前用户(Admin) | PUT | `/admin/me` | ADMIN/AUTHOR/AUDITOR | 修改昵称/邮箱/密码 |
| 获取当前用户(Blog) | GET | `/blog/auth/me` | GUEST | 获取当前登录用户信息 |
| 登出 | POST | `/api/logout` | permitAll | Admin/Blog 共用,清 Cookie + 失效 token,token 过期也能调 |
| OAuth2 登录 | GET | `/oauth2/authorization/{provider}?redirect_uri=xxx` | permitAll | 通过 redirect_uri 参数指定回跳地址 |
| 刷新 token | POST | `/oauth2/token` | refresh_token | grant_type=refresh_token |
| 博客端发验证码 | POST | `/blog/auth/sendCode` | permitAll | 已存在,无改动 |
| 博客端注册 | POST | `/blog/auth/register` | permitAll | 已存在,无改动 |

---

## 八、附:已确认无需前端处理项

- **#7 注册接口校验**:`BlogUserServiceImpl.register` 已校验用户名唯一性、邮箱唯一性、验证码正确性、锁定状态;验证码过期通过 Redis TTL 隐式实现
- **#8 `/admin/admin/*` 旧接口残留**:全局 grep 无匹配,旧接口已彻底清理
- **#13 dev-code 配置**:`application-dev.yml` 已配 `dl.security.dev-code: "123456"`,dev 环境登录可直接用此验证码

---

*文档维护:后端组*
*对应前端改造分支:DriftingLeavesOfFront-v2 v2 升级*
