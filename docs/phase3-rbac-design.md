# DriftingLeaves 阶段三 RBAC 权限设计方案

> **文档定位**：本文件是 [spring-security-oauth2-migration-plan.md](./spring-security-oauth2-migration-plan.md) 阶段三的细化设计文档，定义 4 个核心角色、权限矩阵、落地策略与实施步骤。
> **适用阶段**：阶段三（Resource Server 接入 + 管理员后台迁移）剩余工作
> **核心目标**：实现"一个账号可同时拥有多重身份，角色职责正交不重叠"的 RBAC 权限模型

---

## 一、设计总览

### 1.1 核心设计原则

| 原则 | 说明 |
|------|------|
| **角色正交** | 每个角色负责独立功能域，互不包含、互不重叠 |
| **多重身份** | 通过 `sys_user_role` 多对多关联表，一个账号可同时拥有多个角色 |
| **职责单一** | 角色边界清晰，避免权限混淆 |
| **最小权限** | 默认拒绝，仅显式授权的接口可访问 |
| **叠加生效** | 多角色通过 `hasAnyRole` 自然叠加，权限取并集 |

### 1.2 角色与现有项目的对应关系

| 新角色编码 | 角色名称 | 对应原 user_type 值 | 备注 |
|-----------|---------|-------------------|------|
| `ADMIN` | 超级管理员 | 2 | 系统最高权限 |
| `AUTHOR` | 内容创作者 | 4（新增） | 文章发布与管理（仅自己） |
| `AUDITOR` | 后台审计员 | 3 | 后台只读访问 |
| `GUEST` | 普通访客 | 1 | 前台交互（评论/点赞/留言/订阅） |

### 1.3 与迁移计划文档的差异说明

原迁移计划文档中使用的 `VISITOR`（后台游客）和 `USER`（博客用户）角色编码，本方案重命名为 `AUDITOR` 和 `GUEST`，更准确地表达角色职责：

- `VISITOR` → `AUDITOR`：原"游客"易与未登录访客混淆，"审计员"更明确表达"只读审计"职责
- `USER` → `GUEST`：原"用户"过于泛化，"访客"更贴合"前台交互用户"的定位

> ⚠️ 若迁移计划文档第 2.2 节描述的 `VISITOR`/`USER` 角色编码已被其他地方引用，需要同步更新。

---

## 二、4 个核心角色详细定义

### 2.1 👑 超级管理员（ADMIN）

| 属性 | 值 |
|------|---|
| **角色编码** | `ADMIN` |
| **角色名称** | 超级管理员 |
| **定位** | 系统最高管理者，拥有后台所有权限 |
| **可访问区域** | `/admin/**` 全部 + `/blog/**` 全部 |

#### 权限边界

**系统管理类**：
- 用户管理：封禁/解禁用户、重置密码、分配角色
- 系统配置：网站信息、备案信息、参数配置
- 内容管理：友链、音乐库、个人信息、经历、技能、社交媒体

**内容审核类**：
- 文章审核：通过/拒绝/标记违规
- 文章分类与标签管理
- 评论与留言审核
- **唯一可设置文章置顶的角色**

**数据洞察类**：
- 操作日志查看与清理
- 访问统计（PV/UV）
- 服务器监控（敏感信息）

**特殊能力**：
- 设置文章置顶（`is_top`）
- 审核文章状态变更（`status` 草稿↔待审核↔已发布↔违规）
- 删除任何用户的评论/留言/点赞

#### 与其他角色的关系
- 权限集合最大，理论上包含其他所有角色的操作权限
- 但在 RBAC 模型中独立存在，**不隐含 AUTHOR 权限**
- 若管理员需要发文章，需额外关联 `AUTHOR` 角色

---

### 2.2 ✍️ 内容创作者（AUTHOR）

| 属性 | 值 |
|------|---|
| **角色编码** | `AUTHOR` |
| **角色名称** | 内容创作者 |
| **定位** | 博客内容贡献者，负责生产文章内容 |
| **可访问区域** | `/admin/article/**` 部分 + `/admin/article/comment/**` 部分（仅自己文章） |

#### 权限边界

**文章全生命周期管理（仅自己的文章）**：
- 创建草稿
- 编辑自己的文章（草稿/待审核状态）
- 删除自己的文章
- 提交审核（草稿 → 待审核）
- 发布/下架自己的文章（仅限审核通过后的状态流转）

**协作权限**：
- 作为第一作者邀请其他用户共同创作
- 接受或拒绝他人的协作邀请
- 管理共同作者的编辑权限（`can_edit` 字段）

**评论管理（仅自己文章下的评论）**：
- 查看自己文章的评论列表
- 审核自己文章的评论（通过/拒绝）
- 删除自己文章的评论
- 以作者身份回复评论

**个人管理**：
- 修改自己的个人资料（昵称、头像等）
- 查看自己的文章统计数据

#### 限制
- ❌ 无法访问系统配置（系统配置、友链、音乐等）
- ❌ 无法审核他人文章
- ❌ 无法管理文章分类和标签（只能从已有列表选择）
- ❌ 无法查看操作日志、服务器监控等敏感数据
- ❌ 无法设置文章置顶

#### 数据范围限制
所有文章相关操作必须满足以下条件之一：
1. 当前用户是文章的**第一作者**（`article_authors.author_role = 1`）
2. 当前用户是文章的**共同作者**且 `can_edit = 1`（仅编辑权限，不可删除/提交审核）

---

### 2.3 👁️ 后台审计员（AUDITOR）

| 属性 | 值 |
|------|---|
| **角色编码** | `AUDITOR` |
| **角色名称** | 后台审计员 |
| **定位** | 仅用于查看后台数据，进行审计或监控 |
| **可访问区域** | `/admin/**` 的所有 GET 请求 |

#### 权限边界

**只读访问**：
- 查看文章列表与详情（含所有状态：草稿/待审核/已发布/违规）
- 查看评论列表
- 查看留言列表
- 查看访客列表与浏览记录
- 查看文章分类与标签列表
- 查看友链、音乐、个人信息、经历、技能、社交媒体
- 查看系统配置

**日志与统计**：
- 查看操作日志（用于追溯问题）
- 查看访问统计报表（PV/UV、地域分布、热门文章等）

#### 限制
- ❌ 所有 `POST`、`PUT`、`DELETE` 修改接口均被拒绝（返回 403 Forbidden）
- ❌ 无法访问服务器监控（敏感信息，仅 ADMIN 可见）
- ❌ 无法修改任何数据

#### 设计意图
对应迁移计划文档中"游客账号仅允许只读查询后台"的需求，确保权限最小化。适用于：
- 内容审查员（仅查看内容，不参与审核决策）
- 数据分析师（仅查看统计数据）
- 外部审计人员（仅查看日志）

---

### 2.4 🏠 普通访客（GUEST）

| 属性 | 值 |
|------|---|
| **角色编码** | `GUEST` |
| **角色名称** | 普通访客 |
| **定位** | 注册登录后的普通用户，主要在前端进行交互 |
| **可访问区域** | `/blog/**` 交互接口（评论、点赞、留言、订阅） |

#### 权限边界

**内容交互**：
- 发表评论（含 Markdown 评论）
- 点赞文章（同一文章仅可点赞一次）
- 发送留言（含悄悄话留言）
- 回复他人评论/留言

**订阅服务**：
- 订阅 RSS
- 取消订阅

**个人互动管理**：
- 编辑自己的评论/留言
- 删除自己的评论/留言
- 查看自己的互动历史

#### 限制
- ❌ 无法访问任何 `/admin/**` 后台接口
- ❌ 无法发布文章（需 `AUTHOR` 角色）
- ❌ 无法查看后台数据（需 `AUDITOR` 或 `ADMIN` 角色）

#### 设计意图
对应迁移计划文档中"博客端登录用户"的需求，是阶段四（博客端登录 + 交互接口改造）的核心角色。

---

## 三、多角色身份组合场景

由于数据库设计支持 `sys_user` 和 `sys_role` 的多对多关系（通过 `sys_user_role` 表），可以灵活实现复合身份。

### 3.1 常见角色组合

| 账号类型 | 角色组合 | 能做什么 | 适用场景 |
|---------|---------|---------|---------|
| 纯管理员 | `ADMIN` | 仅后台管理，不发文章不评论 | 运维人员 |
| 站长（全能型） | `ADMIN` + `AUTHOR` + `GUEST` | 管理后台 + 发文章 + 前台交互 | 个人博客站长 |
| 投稿作者 | `AUTHOR` + `GUEST` | 发文章 + 前台交互 | 特邀嘉宾 |
| 内容合伙人 | `AUTHOR` + `AUDITOR` | 发文章 + 查看后台数据 | 核心团队成员 |
| 纯审计员 | `AUDITOR` | 仅查看后台数据 | 外部审计人员 |
| 纯访客 | `GUEST` | 仅前台交互 | 普通注册用户 |

### 3.2 场景详解

#### 场景 A：站长（全能型）
- **组合**：`ADMIN` + `AUTHOR` + `GUEST`
- **描述**：作为站长，既需要管理网站配置（ADMIN权限），又需要亲自写博客文章（AUTHOR权限），平时也在前台浏览和评论（GUEST权限）
- **实现**：在 `sys_user_role` 表中，该用户ID同时关联 `ADMIN`、`AUTHOR`、`GUEST` 三个角色ID
- **效果**：登录后既能看到"系统设置"菜单，也能看到"我的文章"编辑器，还能在前台评论

#### 场景 B：特邀嘉宾（受限创作者）
- **组合**：`AUTHOR` + `GUEST`
- **描述**：邀请的大佬来写文章，他需要写文章（AUTHOR权限），平时也在前台浏览和评论（GUEST权限）
- **实现**：关联 `AUTHOR` 和 `GUEST` 角色
- **效果**：仅能管理自己的文章，无法访问系统配置、无法审核他人内容

#### 场景 C：内容合伙人（创作者+审计员）
- **组合**：`AUTHOR` + `AUDITOR`
- **描述**：核心团队成员。需要写文章（AUTHOR），同时也需要查看网站的访问数据和日志来分析流量（AUDITOR），但无权修改系统配置或封禁用户
- **实现**：关联 `AUTHOR` 和 `AUDITOR` 角色
- **效果**：拥有写文章的权限，同时后台菜单中会显示"数据中心"或"日志审计"入口

### 3.3 角色组合规则

| 组合 | 是否允许 | 说明 |
|------|---------|------|
| `ADMIN` + `AUTHOR` | ✅ 允许 | 管理员兼任作者 |
| `ADMIN` + `AUDITOR` | ✅ 允许（但冗余） | ADMIN 已包含 AUDITOR 的所有读权限 |
| `ADMIN` + `GUEST` | ✅ 允许 | 管理员兼任前台用户 |
| `AUTHOR` + `AUDITOR` | ✅ 允许 | 内容合伙人场景 |
| `AUTHOR` + `GUEST` | ✅ 允许 | 投稿作者场景 |
| `AUDITOR` + `GUEST` | ✅ 允许 | 审计员兼任前台用户 |
| `ADMIN` + `AUTHOR` + `GUEST` | ✅ 允许 | 站长全能场景 |
| 4 角色全有 | ✅ 允许（但冗余） | 超级账号 |

> 💡 **建议**：实际使用中避免给同一用户分配冗余角色（如 `ADMIN` + `AUDITOR`），保持角色组合简洁。

---

## 四、后台接口权限矩阵（/admin/**）

### 4.1 接口权限总览

> 说明：✅=允许 ❌=拒绝（403） ➖=不适用（角色不进入此区域）

| 模块 | Controller 路径 | 接口方法 | ADMIN | AUTHOR | AUDITOR | GUEST | 未登录 |
|------|----------------|---------|:-----:|:------:|:-------:|:-----:|:------:|
| **管理员账号** | | | | | | | |
| - | `/admin/admin/login` | POST | ✅ | ✅ | ✅ | ✅ | ✅(permitAll) |
| - | `/admin/admin/sendCode` | POST | ✅ | ✅ | ✅ | ✅ | ✅(permitAll) |
| - | `/admin/admin/logout` | POST | ✅ | ✅ | ✅ | ✅ | ✅(permitAll) |
| **文章管理** | `/admin/article` | | | | | | |
| 文章列表 | - | GET `/page` | ✅ | ✅(仅自己) | ✅ | ❌ | ❌(401) |
| 文章详情 | - | GET `/{id}` | ✅ | ✅(仅自己) | ✅ | ❌ | ❌(401) |
| 文章搜索 | - | GET `/search` | ✅ | ✅(仅自己) | ✅ | ❌ | ❌(401) |
| 创建文章 | - | POST | ✅ | ✅ | ❌ | ❌ | ❌(401) |
| 编辑文章 | - | PUT | ✅ | ✅(仅自己) | ❌ | ❌ | ❌(401) |
| 删除文章 | - | DELETE | ✅ | ✅(仅自己) | ❌ | ❌ | ❌(401) |
| 更新状态 | - | PUT `/status/{id}` | ✅ | ✅(仅自己) | ❌ | ❌ | ❌(401) |
| **置顶文章** | - | PUT `/top/{id}` | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **文章分类** | `/admin/articleCategory` | | | | | | |
| 分类列表 | - | GET | ✅ | ✅ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除分类 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **文章标签** | `/admin/article/tag` | | | | | | |
| 标签列表 | - | GET | ✅ | ✅ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除标签 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **文章评论** | `/admin/article/comment` | | | | | | |
| 评论列表 | - | GET `/page` | ✅ | ✅(仅自己文章) | ✅ | ❌ | ❌(401) |
| 文章评论列表 | - | GET `/{articleId}` | ✅ | ✅(仅自己文章) | ✅ | ❌ | ❌(401) |
| 审核评论 | - | PUT `/approve` | ✅ | ✅(仅自己文章) | ❌ | ❌ | ❌(401) |
| 删除评论 | - | DELETE | ✅ | ✅(仅自己文章) | ❌ | ❌ | ❌(401) |
| 回复评论 | - | POST `/reply` | ✅ | ✅(仅自己文章) | ❌ | ❌ | ❌(401) |
| **留言** | `/admin/message` | | | | | | |
| 留言列表 | - | GET `/page` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 审核留言 | - | PUT `/approve` | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| 删除留言 | - | DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| 回复留言 | - | POST `/reply` | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **友链** | `/admin/friendLink` | | | | | | |
| 友链列表 | - | GET | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除友链 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **音乐** | `/admin/music` | | | | | | |
| 音乐列表 | - | GET `/page` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 音乐详情 | - | GET `/{id}` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除音乐 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **个人信息** | `/admin/personalInfo` | | | | | | |
| 个人信息 | - | GET | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 修改个人信息 | - | PUT | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **经历** | `/admin/experience` | | | | | | |
| 经历列表 | - | GET | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除经历 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **技能** | `/admin/skill` | | | | | | |
| 技能列表 | - | GET | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除技能 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **社交媒体** | `/admin/socialMedia` | | | | | | |
| 社交媒体列表 | - | GET | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 创建/编辑/删除社交媒体 | - | POST/PUT/DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **系统配置** | `/admin/systemConfig` | | | | | | |
| 系统配置 | - | GET | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 修改系统配置 | - | PUT | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **访客管理** | `/admin/visitor` | | | | | | |
| 访客列表 | - | GET `/page` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 封禁/解禁访客 | - | PUT `/block`, `/unblock` | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| 删除访客 | - | DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **浏览记录** | `/admin/view` | | | | | | |
| 浏览列表 | - | GET `/page` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 删除浏览记录 | - | DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **操作日志** | `/admin/operationLog` | | | | | | |
| 日志列表 | - | GET `/page` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 删除日志 | - | DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **RSS订阅** | `/admin/rssSubscription` | | | | | | |
| 订阅列表 | - | GET `/page`, `/{id}` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 修改订阅 | - | PUT | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| 删除订阅 | - | DELETE | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **报表** | `/admin/report` | | | | | | |
| 访问统计 | - | GET `/viewStatistics` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 访客统计 | - | GET `/visitorStatistics` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 省份分布 | - | GET `/provinceDistribution` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 文章浏览Top10 | - | GET `/articleViewTop10` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| 后台概览 | - | GET `/overview` | ✅ | ❌ | ✅ | ❌ | ❌(401) |
| **服务器监控** | `/admin/server-monitor` | | | | | | |
| 全部监控接口 | - | GET（所有） | ✅ | ❌ | ❌ | ❌ | ❌(401) |
| **通用** | `/admin/common` | | | | | | |
| 文件上传 | - | POST `/upload` | ✅ | ✅ | ❌ | ❌ | ❌(401) |

### 4.2 权限矩阵说明

#### 4.2.1 AUDITOR 的 GET 例外
- **服务器监控**：虽然是 GET 请求，但涉及敏感信息（CPU、内存、磁盘、网络），仅 ADMIN 可访问
- **其他 GET**：AUDITOR 可访问所有 `/admin/**` 的 GET 接口（除服务器监控外）

#### 4.2.2 AUTHOR 的数据范围限制
所有标记为"仅自己"的接口，需要在方法级通过 `@PreAuthorize` 调用 `ArticlePermissionService` 进行校验：

```java
@PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isAuthor(#id, authentication.name))")
```

#### 4.2.3 GUEST 角色在 /admin/** 的权限
- GUEST 角色对 `/admin/**` 所有接口均 403
- GUEST 仅能访问 `/blog/**` 下的交互接口
- 在 ResourceServerConfig 中通过 URL 粗粒度配置拒绝 GUEST 访问后台

---

## 五、权限落地策略

### 5.1 三层权限控制架构

```text
┌─────────────────────────────────────────────────────────────┐
│  第一层：URL 级粗粒度（ResourceServerConfig）                │
│  ─────────────────────────────────────────────────────────  │
│  基于 HTTP 方法和 URL 模式匹配，快速拒绝非法请求              │
│  适用于：GET 通用放行、非 GET 通用拦截、敏感路径单独控制       │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  第二层：类级 @PreAuthorize（Controller 类注解）              │
│  ─────────────────────────────────────────────────────────  │
│  控制整个 Controller 模块的访问权限，排除不相关角色            │
│  适用于：留言/友链/音乐等模块排除 AUTHOR 访问                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  第三层：方法级 @PreAuthorize（Controller 方法注解）          │
│  ─────────────────────────────────────────────────────────  │
│  精细控制每个接口的权限，支持 SpEL 表达式调用权限服务          │
│  适用于：AUTHOR 数据范围校验、ADMIN 专属操作（审核/置顶）      │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 第一层：ResourceServerConfig 粗粒度配置

修改 `DL-server/src/main/java/com/xuan/resource/config/ResourceServerConfig.java`：

```java
.authorizeHttpRequests(auth -> auth
    // ===== 公开端点（无需认证）=====
    .requestMatchers("/oauth2/**", 
                      "/admin/admin/login", 
                      "/admin/admin/sendCode", 
                      "/admin/admin/logout").permitAll()
    
    // ===== 敏感路径：仅 ADMIN =====
    // 服务器监控涉及敏感信息，仅 ADMIN 可访问
    .requestMatchers("/admin/server-monitor/**").hasRole("ADMIN")
    
    // ===== 后台 GET 请求：ADMIN + AUTHOR + AUDITOR =====
    // GUEST 角色被排除，无法访问后台任何 GET 接口
    .requestMatchers(HttpMethod.GET, "/admin/**").hasAnyRole("ADMIN", "AUTHOR", "AUDITOR")
    
    // ===== 后台非 GET 请求：ADMIN + AUTHOR =====
    // AUDITOR 和 GUEST 被排除，AUDITOR 仅读，GUEST 不进后台
    // AUTHOR 在方法级再细控（仅自己的文章）
    .requestMatchers(HttpMethod.POST, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
    .requestMatchers(HttpMethod.PUT, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
    .requestMatchers(HttpMethod.DELETE, "/admin/**").hasAnyRole("ADMIN", "AUTHOR")
    
    // ===== 其他路径：暂时放行（blog/cv/home 在阶段四处理）=====
    .anyRequest().permitAll()
)
```

### 5.3 第二层：类级 @PreAuthorize 配置

对于 AUTHOR 不应访问的模块，在 Controller 类级别添加注解：

```java
// ===== 仅 ADMIN + AUDITOR 可访问的模块（AUTHOR 被排除）=====

@RestController
@RequestMapping("/admin/message")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")  // AUTHOR 不可访问留言模块
public class MessageController { ... }

@RestController
@RequestMapping("/admin/friendLink")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class FriendLinkController { ... }

@RestController
@RequestMapping("/admin/music")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class MusicController { ... }

@RestController
@RequestMapping("/admin/personalInfo")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class PersonalInfoController { ... }

@RestController
@RequestMapping("/admin/experience")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class ExperienceController { ... }

@RestController
@RequestMapping("/admin/skill")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class SkillController { ... }

@RestController
@RequestMapping("/admin/socialMedia")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class SocialMediaController { ... }

@RestController
@RequestMapping("/admin/systemConfig")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class SystemConfigController { ... }

@RestController
@RequestMapping("/admin/visitor")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class VisitorController { ... }

@RestController
@RequestMapping("/admin/view")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class ViewController { ... }

@RestController
@RequestMapping("/admin/operationLog")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class OperationLogController { ... }

@RestController
@RequestMapping("/admin/rssSubscription")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class RssSubscriptionController { ... }

@RestController
@RequestMapping("/admin/report")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class ReportController { ... }

@RestController
@RequestMapping("/admin/articleCategory")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")  // 作者只能读分类，不能管理
public class ArticleCategoryController { ... }

@RestController
@RequestMapping("/admin/article/tag")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")  // 作者只能读标签，不能管理
public class ArticleTagController { ... }

// ===== 仅 ADMIN 可访问的模块 =====

@RestController
@RequestMapping("/admin/server-monitor")
@PreAuthorize("hasRole('ADMIN')")  // 服务器监控仅 ADMIN
public class ServerMonitorController { ... }

// ===== ADMIN + AUTHOR 可访问的模块（无需类级注解，由第一层兜底）=====

// ArticleController：无类级注解，方法级细控
// ArticleCommentController：无类级注解，方法级细控
// CommonController：无类级注解（文件上传 ADMIN + AUTHOR 都需要）
```

### 5.4 第三层：方法级 @PreAuthorize 配置

#### 5.4.1 ArticleController 方法级注解

```java
@RestController
@RequestMapping("/admin/article")
public class ArticleController {

    // ===== 查询类：ADMIN + AUTHOR + AUDITOR =====
    // AUDITOR 能看所有文章，AUTHOR 在 Service 层只返回自己的文章
    
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<PageResult<ArticleVO>> pageQuery(ArticlePageQueryDTO dto) { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<Articles> getArticleById(@PathVariable Long id) { ... }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<PageResult> search(...) { ... }

    // ===== 创建类：ADMIN + AUTHOR =====
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
    public Result createArticle(@RequestBody ArticleDTO dto,
                                 @AuthenticationPrincipal SecurityUser user) { ... }

    // ===== 编辑/删除类：ADMIN 或 AUTHOR（仅自己的文章）=====
    
    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isAuthor(#articleDTO.id, authentication.name))")
    public Result updateArticle(@RequestBody ArticleDTO articleDTO) { ... }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isFirstAuthor(#ids, authentication.name))")
    public Result batchDelete(@RequestParam List<Long> ids) { ... }

    // ===== 状态管理：ADMIN 或 AUTHOR（仅自己的文章）=====
    
    @PutMapping("/status/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isAuthor(#id, authentication.name))")
    public Result updateStatus(@PathVariable Long id, @RequestParam Integer status) { ... }

    // ===== 审核类：仅 ADMIN =====
    // 注意：当前接口未单独提供 audit 端点，通过 updateStatus 实现
    // 若 status 变更为 2（已发布）或 3（违规），需要 ADMIN 权限
    // 建议拆分为独立端点：
    
    @PutMapping("/audit/{id}")
    @PreAuthorize("hasRole('ADMIN')")  // 仅 ADMIN 可审核
    public Result auditArticle(@PathVariable Long id, @RequestParam Integer status) { ... }

    // ===== 置顶类：仅 ADMIN =====
    
    @PutMapping("/top/{id}")
    @PreAuthorize("hasRole('ADMIN')")  // 仅 ADMIN 可置顶
    public Result toggleTop(@PathVariable Long id, @RequestParam Integer isTop) { ... }
}
```

#### 5.4.2 ArticleCommentController 方法级注解

```java
@RestController
@RequestMapping("/admin/article/comment")
public class ArticleCommentController {

    // ===== 查询类：ADMIN + AUTHOR + AUDITOR =====
    // AUTHOR 在 Service 层只返回自己文章的评论
    
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<PageResult> pageQuery(...) { ... }

    @GetMapping("/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<List<ArticleComments>> getByArticleId(@PathVariable Long articleId) { ... }

    // ===== 管理类：ADMIN 或 AUTHOR（仅自己文章的评论）=====
    
    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.areCommentsInOwnArticle(#ids, authentication.name))")
    public Result<String> batchApprove(@RequestParam List<Long> ids) { ... }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.areCommentsInOwnArticle(#ids, authentication.name))")
    public Result<String> batchDelete(@RequestParam List<Long> ids) { ... }

    @PostMapping("/reply")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isCommentInOwnArticle(#articleCommentReplyDTO.parentId, authentication.name))")
    public Result<String> adminReply(@RequestBody ArticleCommentReplyDTO dto) { ... }
}
```

#### 5.4.3 管理类模块方法级注解

```java
// 对于类级已限制为 ADMIN + AUDITOR 的模块，写操作仅需限制 ADMIN

@RestController
@RequestMapping("/admin/message")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class MessageController {

    @GetMapping("/page")
    // 类级注解已覆盖，无需方法级注解
    public Result<PageResult> pageQuery(...) { ... }

    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")  // 仅 ADMIN 可审核
    public Result<String> batchApprove(...) { ... }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> batchDelete(...) { ... }

    @PostMapping("/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> adminReply(...) { ... }
}

// 其他管理类模块（友链、音乐、经历、技能等）同理
```

---

## 六、ArticlePermissionService 设计

### 6.1 接口定义

新建文件：`DL-server/src/main/java/com/xuan/service/ArticlePermissionService.java`

```java
package com.xuan.service;

import java.util.List;

/**
 * 文章权限校验服务
 * 用于 @PreAuthorize 注解中调用，校验 AUTHOR 角色的数据范围
 */
public interface ArticlePermissionService {

    /**
     * 判断当前用户是否为指定文章的作者（含共同作者）
     * @param articleId 文章ID
     * @param username 当前用户名
     * @return true=是作者
     */
    boolean isAuthor(Long articleId, String username);

    /**
     * 判断当前用户是否为指定文章的第一作者
     * 第一作者可删除文章、提交审核、邀请共同作者
     * @param articleId 文章ID
     * @param username 当前用户名
     * @return true=是第一作者
     */
    boolean isFirstAuthor(Long articleId, String username);

    /**
     * 批量判断：当前用户是否为所有指定文章的第一作者
     * 用于批量删除场景
     * @param articleIds 文章ID列表
     * @param username 当前用户名
     * @return true=全部为第一作者
     */
    boolean isFirstAuthor(List<Long> articleIds, String username);

    /**
     * 判断指定评论是否在当前用户自己的文章下
     * @param commentId 评论ID
     * @param username 当前用户名
     * @return true=评论在自己的文章下
     */
    boolean isCommentInOwnArticle(Long commentId, String username);

    /**
     * 批量判断：指定评论是否全部在当前用户自己的文章下
     * @param commentIds 评论ID列表
     * @param username 当前用户名
     * @return true=全部在自己文章下
     */
    boolean areCommentsInOwnArticle(List<Long> commentIds, String username);
}
```

### 6.2 实现类

新建文件：`DL-server/src/main/java/com/xuan/service/impl/ArticlePermissionServiceImpl.java`

```java
package com.xuan.service.impl;

import com.xuan.entity.ArticleAuthors;
import com.xuan.entity.ArticleComments;
import com.xuan.entity.SysUser;
import com.xuan.mapper.ArticleAuthorsMapper;
import com.xuan.mapper.ArticleCommentMapper;
import com.xuan.mapper.SysUserMapper;
import com.xuan.service.ArticlePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticlePermissionServiceImpl implements ArticlePermissionService {

    private final ArticleAuthorsMapper articleAuthorsMapper;
    private final ArticleCommentMapper articleCommentMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public boolean isAuthor(Long articleId, String username) {
        if (articleId == null || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            return false;
        }
        // 查询 article_authors 表，判断用户是否为该文章的作者（任意角色）
        return articleAuthorsMapper.existsByArticleIdAndUserId(articleId, userId);
    }

    @Override
    public boolean isFirstAuthor(Long articleId, String username) {
        if (articleId == null || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            return false;
        }
        // 查询 article_authors 表，判断用户是否为该文章的第一作者（author_role = 1）
        return articleAuthorsMapper.existsFirstAuthorByArticleIdAndUserId(articleId, userId);
    }

    @Override
    public boolean isFirstAuthor(List<Long> articleIds, String username) {
        if (articleIds == null || articleIds.isEmpty() || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            return false;
        }
        // 批量查询：所有文章的第一作者都必须是当前用户
        Long count = articleAuthorsMapper.countFirstAuthorByArticleIdsAndUserId(articleIds, userId);
        return count != null && count == articleIds.size();
    }

    @Override
    public boolean isCommentInOwnArticle(Long commentId, String username) {
        if (commentId == null || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            return false;
        }
        // 通过评论ID查询对应的文章ID，再判断用户是否为该文章的作者
        ArticleComments comment = articleCommentMapper.selectById(commentId);
        if (comment == null) {
            return false;
        }
        return isAuthor(comment.getArticleId(), username);
    }

    @Override
    public boolean areCommentsInOwnArticle(List<Long> commentIds, String username) {
        if (commentIds == null || commentIds.isEmpty() || username == null) {
            return false;
        }
        // 逐个判断（数据量通常不大）
        for (Long commentId : commentIds) {
            if (!isCommentInOwnArticle(commentId, username)) {
                return false;
            }
        }
        return true;
    }

    private Long getUserIdByUsername(String username) {
        SysUser user = sysUserMapper.selectByUsername(username);
        return user != null ? user.getId() : null;
    }
}
```

### 6.3 Mapper 补充方法

需要在 `ArticleAuthorsMapper` 中补充以下方法：

```java
@Mapper
public interface ArticleAuthorsMapper extends BaseMapper<ArticleAuthors> {

    /**
     * 判断用户是否为指定文章的作者（任意角色）
     */
    @Select("SELECT COUNT(1) FROM article_authors WHERE article_id = #{articleId} AND user_id = #{userId} AND invite_status = 1")
    boolean existsByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 判断用户是否为指定文章的第一作者
     */
    @Select("SELECT COUNT(1) FROM article_authors WHERE article_id = #{articleId} AND user_id = #{userId} AND author_role = 1 AND invite_status = 1")
    boolean existsFirstAuthorByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 批量统计：用户作为第一作者的文章数量
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM article_authors WHERE user_id = #{userId} AND author_role = 1 AND invite_status = 1 " +
            "AND article_id IN " +
            "<foreach collection='articleIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    Long countFirstAuthorByArticleIdsAndUserId(@Param("articleIds") List<Long> articleIds, @Param("userId") Long userId);
}
```

---

## 七、数据库改动

### 7.1 修改 sys_role 表初始数据

修改 `docs/DriftingLeaves.sql` 第 663-666 行：

```sql
-- 原始版本
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '管理员', '后台管理员，拥有全部管理权限'),
('VISITOR', '后台游客', '后台游客账号，仅允许只读查询'),
('USER', '博客用户', '博客端登录用户，可评论、留言、点赞、订阅');

-- 修改为
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '超级管理员', '系统最高权限，包括用户管理、配置、审核、监控等'),
('AUTHOR', '内容创作者', '文章发布与管理，仅限自己的文章'),
('AUDITOR', '后台审计员', '后台只读权限，可查看文章、日志和统计，不可修改'),
('GUEST', '普通访客', '前台交互权限，可评论、点赞、留言、订阅');
```

### 7.2 修改 sys_user 表注释

修改 `docs/DriftingLeaves.sql` 第 73 行：

```sql
-- 原始版本
user_type TINYINT NOT NULL DEFAULT 1 COMMENT '用户类型：1博客用户 2管理员 3后台游客',

-- 修改为（弱化 user_type，仅作主身份展示用）
user_type TINYINT NOT NULL DEFAULT 1 COMMENT '用户主身份（仅展示用）：1普通访客 2管理员 3审计员 4创作者。实际权限以 sys_user_role 为准',
```

### 7.3 修改初始用户数据

修改 `docs/DriftingLeaves.sql` 第 670-689 行：

```sql
-- 插入管理员账号（同时拥有 ADMIN + AUTHOR + GUEST 三重身份，方便站长使用）
INSERT INTO sys_user (username, password, nickname, email, user_type, status, login_type, create_time, update_time)
VALUES ('admin', '$2a$12$RNmabKbadyselUJuGhbn8u0RNGQ6OVL4TCGVgbjQRpyaF6a00zZxG', '管理员', 'admin@example.com', 2, 1, 1, NOW(), NOW());

-- 管理员关联三重角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code IN ('ADMIN', 'AUTHOR', 'GUEST');

-- 插入后台审计员账号
INSERT INTO sys_user (username, password, nickname, email, user_type, status, login_type, create_time, update_time)
VALUES ('auditor', '$2a$12$BVbiSFusGtwNj2uRXZkys.C1.8cNlp1oN0Yug/ejOdVuOXz4be6Wq', '审计员', '', 3, 1, 1, NOW(), NOW());

-- 审计员关联 AUDITOR 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'auditor' AND r.role_code = 'AUDITOR';

-- 插入测试作者账号（AUTHOR + GUEST）
INSERT INTO sys_user (username, password, nickname, email, user_type, status, login_type, create_time, update_time)
VALUES ('author', '$2a$12$BVbiSFusGtwNj2uRXZkys.C1.8cNlp1oN0Yug/ejOdVuOXz4be6Wq', '测试作者', 'author@example.com', 4, 1, 1, NOW(), NOW());

-- 作者关联 AUTHOR + GUEST 角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'author' AND r.role_code IN ('AUTHOR', 'GUEST');
```

### 7.4 OAuth2 客户端配置调整

OAuth2 客户端配置保持不变，`admin-client` 用于后台登录，`blog-client` 用于前台登录。但需要在 `admin-client` 的 scopes 中追加 `auditor` 和 `author`：

```sql
-- 修改 admin-client 的 scopes
UPDATE oauth2_registered_client 
SET scopes = 'openid,profile,admin,author,auditor' 
WHERE client_id = 'admin-client';

-- blog-client 保持不变
-- scopes = 'openid,profile,user' （对应 GUEST 角色）
```

> 💡 **注意**：scope 与 role 的映射关系需要在 `UserDetailsServiceImpl` 中维护，用户的角色取决于 `sys_user_role` 表的关联，而非 scope。

---

## 八、JwtCustomizerConfig 调整

### 8.1 当前实现

当前 `JwtCustomizerConfig` 已将用户角色写入 JWT 的 `roles` claim：

```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
    return context -> {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            Set<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
            context.getClaims().claim("roles", roles);
        }
    };
}
```

### 8.2 多角色场景下的 JWT 示例

用户 `admin` 同时拥有 `ADMIN` + `AUTHOR` + `GUEST` 三个角色，JWT payload：

```json
{
  "sub": "admin",
  "aud": ["admin-client"],
  "scope": ["openid", "profile", "admin", "author", "auditor"],
  "roles": ["ROLE_ADMIN", "ROLE_AUTHOR", "ROLE_GUEST"],
  "iss": "http://localhost:5922",
  "exp": 1784389967,
  "iat": 1784388167,
  "jti": "391c6245-e4b8-4a70-867b-d7b9c8238bb1"
}
```

### 8.3 ResourceServerConfig 已支持多角色解析

当前 `JwtAuthenticationConverter` 已正确配置，无需修改：

```java
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
```

---

## 九、实施步骤与推进顺序

### 9.1 总体推进顺序

```text
步骤 1：数据库改动
    │   修改 sys_role 表、sys_user 表注释、初始用户数据
    ↓
步骤 2：ArticlePermissionService 开发
    │   新建接口与实现类、补充 Mapper 方法
    ↓
步骤 3：ResourceServerConfig 改造
    │   调整 URL 级粗粒度权限配置
    ↓
步骤 4：Controller 类级 @PreAuthorize 注解
    │   为 15 个管理类 Controller 添加类级注解
    ↓
步骤 5：Controller 方法级 @PreAuthorize 注解
    │   为 ArticleController、ArticleCommentController 添加方法级注解
    ↓
步骤 6：Token Cookie 下发机制
    │   实现 OAuth2TokenResponseCookieHandler、CookieBearerTokenResolver
    ↓
步骤 7：旧代码清理
    │   删除 JwtTokenAdminInterceptor、TokenService 等
    ↓
步骤 8：验收测试
    │   按角色矩阵逐项验证
```

### 9.2 各步骤详细说明

#### 步骤 1：数据库改动
- 修改 `docs/DriftingLeaves.sql` 中 `sys_role` 初始数据（4 个角色）
- 修改 `sys_user.user_type` 字段注释
- 修改初始用户数据（admin 三重身份、auditor 测试账号、author 测试账号）
- 重新执行 SQL 初始化数据库

#### 步骤 2：ArticlePermissionService 开发
- 新建 `ArticlePermissionService` 接口
- 新建 `ArticlePermissionServiceImpl` 实现类
- 在 `ArticleAuthorsMapper` 中补充 3 个查询方法
- 编写单元测试验证权限判断逻辑

#### 步骤 3：ResourceServerConfig 改造
- 修改 `ResourceServerConfig.java` 的 `authorizeHttpRequests` 配置
- 添加服务器监控路径单独控制
- 添加 GET/POST/PUT/DELETE 分方法控制

#### 步骤 4：Controller 类级 @PreAuthorize 注解
为以下 15 个 Controller 添加类级注解：

| Controller | 类级注解 |
|-----------|---------|
| MessageController | `@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")` |
| FriendLinkController | 同上 |
| MusicController | 同上 |
| PersonalInfoController | 同上 |
| ExperienceController | 同上 |
| SkillController | 同上 |
| SocialMediaController | 同上 |
| SystemConfigController | 同上 |
| VisitorController | 同上 |
| ViewController | 同上 |
| OperationLogController | 同上 |
| RssSubscriptionController | 同上 |
| ReportController | 同上 |
| ArticleCategoryController | 同上 |
| ArticleTagController | 同上 |
| ServerMonitorController | `@PreAuthorize("hasRole('ADMIN')")` |

#### 步骤 5：Controller 方法级 @PreAuthorize 注解
- `ArticleController`：8 个方法添加注解（查询类、创建类、编辑/删除类、状态管理、置顶）
- `ArticleCommentController`：5 个方法添加注解（查询类、审核/删除/回复类）
- 其他管理类 Controller 的写操作方法添加 `@PreAuthorize("hasRole('ADMIN')")`

#### 步骤 6：Token Cookie 下发机制
- 新建 `OAuth2TokenResponseCookieHandler`（实现 `AuthenticationSuccessHandler`）
- 新建 `CookieBearerTokenResolver`（实现 `BearerTokenResolver`）
- 修改 `AuthorizationServerConfig` 注册 `accessTokenResponseHandler`
- 修改 `ResourceServerConfig` 注册 `bearerTokenResolver`

#### 步骤 7：旧代码清理
- 删除 `JwtTokenAdminInterceptor.java`
- 删除 `TokenService.java` 和 `TokenServiceImpl.java`
- 删除 `EncryptPasswordService.java` 和 `EncryptPasswordServiceImpl.java`
- 修改 `WebMvcConfiguration.java` 移除 `jwtTokenAdminInterceptor` 注入字段
- 修改 `OperationLogAspect.java` 从 `SecurityContextHolder` 取 `user_id`

#### 步骤 8：验收测试
按角色矩阵逐项验证，详见第 10 节。

---

## 十、验收标准与测试用例

### 10.1 验收标准

#### 10.1.1 数据库层验收
- [ ] `sys_role` 表包含 4 个角色：ADMIN、AUTHOR、AUDITOR、GUEST
- [ ] `sys_user.user_type` 字段注释已更新
- [ ] `admin` 账号关联 ADMIN + AUTHOR + GUEST 三个角色
- [ ] `auditor` 账号关联 AUDITOR 角色
- [ ] `author` 账号关联 AUTHOR + GUEST 角色

#### 10.1.2 JWT Token 验收
- [ ] `admin` 登录后 JWT 的 `roles` claim 包含 `["ROLE_ADMIN", "ROLE_AUTHOR", "ROLE_GUEST"]`
- [ ] `auditor` 登录后 JWT 的 `roles` claim 包含 `["ROLE_AUDITOR"]`
- [ ] `author` 登录后 JWT 的 `roles` claim 包含 `["ROLE_AUTHOR", "ROLE_GUEST"]`

#### 10.1.3 接口权限验收

**ADMIN 角色测试**：
- [ ] GET `/admin/article/page` → 200
- [ ] POST `/admin/article` → 200
- [ ] PUT `/admin/article/top/{id}` → 200（置顶）
- [ ] GET `/admin/server-monitor/overview` → 200
- [ ] GET `/admin/operationLog/page` → 200
- [ ] DELETE `/admin/operationLog` → 200

**AUTHOR 角色测试**（仅有 AUTHOR，无 ADMIN）：
- [ ] GET `/admin/article/page` → 200（仅返回自己的文章）
- [ ] POST `/admin/article` → 200
- [ ] PUT `/admin/article` → 200（仅自己的文章）
- [ ] PUT `/admin/article/top/{id}` → 403（不能置顶）
- [ ] GET `/admin/server-monitor/overview` → 403
- [ ] GET `/admin/message/page` → 403（不能访问留言模块）
- [ ] GET `/admin/operationLog/page` → 403

**AUDITOR 角色测试**：
- [ ] GET `/admin/article/page` → 200
- [ ] GET `/admin/message/page` → 200
- [ ] GET `/admin/operationLog/page` → 200
- [ ] GET `/admin/server-monitor/overview` → 403（敏感信息）
- [ ] POST `/admin/article` → 403
- [ ] PUT `/admin/article` → 403
- [ ] DELETE `/admin/article` → 403

**GUEST 角色测试**：
- [ ] GET `/admin/article/page` → 403（不进后台）
- [ ] POST `/admin/article` → 403
- [ ] 所有 `/admin/**` → 403

**未登录测试**：
- [ ] 所有 `/admin/**`（除 login/sendCode/logout）→ 401
- [ ] POST `/admin/admin/login` → 200（permitAll）

#### 10.1.4 多角色组合验收
- [ ] `admin`（ADMIN + AUTHOR + GUEST）能访问所有后台接口 + 发文章
- [ ] `author`（AUTHOR + GUEST）能发文章 + 前台交互，但不能访问其他后台模块
- [ ] `auditor`（AUDITOR）能查看后台数据，但不能修改

#### 10.1.5 Cookie 下发验收
- [ ] POST `/oauth2/token` 成功后，响应头包含 `Set-Cookie: access_token=...; HttpOnly`
- [ ] 浏览器后续请求自动携带 Cookie
- [ ] 旧的无 Cookie 请求（仅带 Authorization Header）仍能正常工作

#### 10.1.6 旧代码清理验收
- [ ] `JwtTokenAdminInterceptor.java` 已删除
- [ ] `TokenService.java` 已删除
- [ ] `WebMvcConfiguration.java` 中无 `jwtTokenAdminInterceptor` 引用
- [ ] 项目能正常启动，无 Bean 装配失败
- [ ] 操作日志记录的 `user_id` 正确

### 10.2 测试脚本

#### 10.2.1 获取不同角色的 Token

```bash
# ADMIN 角色 Token
curl -X POST http://localhost:5922/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "admin-client:admin-client-secret" \
  -d "grant_type=admin_password_code" \
  -d "username=admin" \
  -d "password=admin-password" \
  -d "code=123456"

# AUTHOR 角色 Token（需要先创建 author 账号）
curl -X POST http://localhost:5922/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "admin-client:admin-client-secret" \
  -d "grant_type=admin_password_code" \
  -d "username=author" \
  -d "password=author-password" \
  -d "code=123456"

# AUDITOR 角色 Token
curl -X POST http://localhost:5922/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "admin-client:admin-client-secret" \
  -d "grant_type=admin_password_code" \
  -d "username=auditor" \
  -d "password=auditor-password" \
  -d "code=123456"
```

#### 10.2.2 验证接口权限

```bash
# AUTHOR 访问留言模块（应 403）
curl -X GET http://localhost:5922/admin/message/page \
  -H "Authorization: Bearer ${AUTHOR_TOKEN}"

# AUDITOR 创建文章（应 403）
curl -X POST http://localhost:5922/admin/article \
  -H "Authorization: Bearer ${AUDITOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"title":"test","content":"test"}'

# AUDITOR 访问服务器监控（应 403）
curl -X GET http://localhost:5922/admin/server-monitor/overview \
  -H "Authorization: Bearer ${AUDITOR_TOKEN}"

# GUEST 访问后台（应 403）
curl -X GET http://localhost:5922/admin/article/page \
  -H "Authorization: Bearer ${GUEST_TOKEN}"
```

---

## 十一、与后续阶段的衔接

### 11.1 与阶段四的衔接（博客端登录 + 交互接口改造）

阶段三为阶段四提供了以下基础：

1. **GUEST 角色已定义**：阶段四的博客端登录用户直接使用 `GUEST` 角色
2. **OAuth2 客户端已预置**：`blog-client` 已在 `oauth2_registered_client` 表中
3. **JWT 多角色支持**：ResourceServerConfig 已支持多角色解析
4. **权限框架已就绪**：阶段四仅需在 `/blog/**` 接口添加 `@PreAuthorize("hasRole('GUEST')")`

阶段四需要做的工作：

```java
// 阶段四：博客端交互接口添加权限控制
@RestController
@RequestMapping("/blog/comment")
public class ArticleCommentController {

    @PostMapping
    @PreAuthorize("hasRole('GUEST')")  // 新增：仅 GUEST 可评论
    public Result addComment(@RequestBody ArticleCommentDTO dto,
                             @AuthenticationPrincipal SecurityUser user) {
        dto.setUserId(user.getUserId());
        commentService.addComment(dto);
        return Result.success();
    }
}
```

### 11.2 与阶段五的衔接（前端适配）

阶段三的多角色设计对前端的影响：

1. **菜单动态渲染**：前端根据 JWT 中的 `roles` claim 动态显示菜单
   - 有 `ROLE_ADMIN` → 显示"系统管理"菜单
   - 有 `ROLE_AUTHOR` → 显示"我的文章"菜单
   - 有 `ROLE_AUDITOR` → 显示"数据中心"菜单
   - 仅有 `ROLE_GUEST` → 不显示后台入口

2. **登录页调整**：登录后根据角色跳转不同页面
   - 有 `ROLE_ADMIN`/`ROLE_AUTHOR`/`ROLE_AUDITOR` → 跳转后台
   - 仅有 `ROLE_GUEST` → 跳转前台

3. **Token 刷新**：前端统一处理 401 自动刷新，无需区分角色

---

## 十二、风险与应对

| 风险 | 应对措施 | 回滚方案 |
|------|---------|---------|
| AUTHOR 数据范围校验失效，导致越权访问 | 在 Service 层增加二次校验，不仅仅依赖 @PreAuthorize | 临时将 AUTHOR 权限降级为 AUDITOR |
| 多角色组合导致权限判断混乱 | 严格遵循"角色正交"原则，避免角色间权限重叠 | 简化为单角色模式 |
| ArticlePermissionService 性能问题 | 增加 Redis 缓存，缓存用户与文章的关联关系 | 临时关闭数据范围校验 |
| Cookie 下发与 CORS 冲突 | 确保 CORS 配置允许 `allowCredentials=true` | 回退到 Authorization Header 方式 |
| 旧代码清理导致启动失败 | 分批清理，每删一个文件就启动测试 | 从 Git 历史恢复 |

---

## 十三、附录

### 13.1 相关文件清单

#### 需要修改的文件
| 文件路径 | 修改内容 |
|---------|---------|
| `docs/DriftingLeaves.sql` | 修改 sys_role 初始数据、sys_user 注释、初始用户数据 |
| `DL-server/.../resource/config/ResourceServerConfig.java` | 调整 URL 级权限配置 |
| `DL-server/.../controller/admin/*.java`（19 个） | 添加类级/方法级 @PreAuthorize 注解 |
| `DL-server/.../config/WebMvcConfiguration.java` | 移除 jwtTokenAdminInterceptor 引用 |
| `DL-server/.../aspect/OperationLogAspect.java` | 改为从 SecurityContextHolder 取 user_id |

#### 需要新建的文件
| 文件路径 | 用途 |
|---------|------|
| `DL-server/.../service/ArticlePermissionService.java` | 文章权限校验接口 |
| `DL-server/.../service/impl/ArticlePermissionServiceImpl.java` | 文章权限校验实现 |
| `DL-server/.../auth/security/OAuth2TokenResponseCookieHandler.java` | Token Cookie 下发 |
| `DL-server/.../resource/config/CookieBearerTokenResolver.java` | Cookie Token 解析 |

#### 需要删除的文件
| 文件路径 | 原因 |
|---------|------|
| `DL-server/.../interceptor/JwtTokenAdminInterceptor.java` | 已由 Resource Server 接管 |
| `DL-server/.../service/TokenService.java` | 旧 JWT 服务 |
| `DL-server/.../service/impl/TokenServiceImpl.java` | 旧 JWT 服务实现 |
| `DL-server/.../service/EncryptPasswordService.java` | 旧盐值加密 |
| `DL-server/.../service/impl/EncryptPasswordServiceImpl.java` | 旧盐值加密实现 |

### 13.2 角色与 scope 映射关系

| 角色编码 | OAuth2 scope | 说明 |
|---------|-------------|------|
| `ADMIN` | `admin` | 管理员 scope |
| `AUTHOR` | `author` | 创作者 scope |
| `AUDITOR` | `auditor` | 审计员 scope |
| `GUEST` | `user` | 访客 scope（保持与 blog-client 一致） |

> 💡 **注意**：scope 与 role 是两个独立的概念。scope 表示客户端被授权访问的范围，role 表示用户的实际权限。用户的 role 由 `sys_user_role` 表决定，与 scope 无直接关系。scope 主要用于 OAuth2 协议层，role 用于应用层权限控制。

### 13.3 常见问题

#### Q1：为什么 AUDITOR 不能访问服务器监控？
A：服务器监控涉及 CPU、内存、磁盘、网络等敏感系统信息，仅 ADMIN 可访问。AUDITOR 主要用于内容审计和数据统计，不涉及系统级信息。

#### Q2：AUTHOR 为什么不能管理文章分类和标签？
A：文章分类和标签是全站共享的资源，管理权限仅属于 ADMIN。AUTHOR 只能从已有列表中选择分类和标签，避免作者随意创建/删除影响全站结构。

#### Q3：为什么 GUEST 角色不能访问后台任何接口？
A：GUEST 角色定位为"前台交互用户"，与后台管理完全隔离。若一个 GUEST 用户需要查看后台，应额外关联 `AUDITOR` 角色。

#### Q4：多角色组合会导致权限叠加吗？
A：是的，多角色权限取并集。例如 `ADMIN` + `AUTHOR` 的用户既能管理后台又能发文章。这是 RBAC 模型的标准行为。

#### Q5：AUTHOR 编辑文章时，如何防止越权修改他人文章？
A：通过 `@PreAuthorize` 注解调用 `ArticlePermissionService.isAuthor()` 方法，在方法执行前校验当前用户是否为该文章的作者。同时建议在 Service 层增加二次校验，确保数据安全。

---

## 十四、总结

本方案基于 RBAC 模型设计了 4 个正交角色（ADMIN/AUTHOR/AUDITOR/GUEST），通过 `sys_user_role` 多对多关联表实现多重身份组合。权限控制采用三层架构：URL 级粗粒度、类级模块控制、方法级精细校验。AUTHOR 角色的数据范围限制通过 `ArticlePermissionService` 实现，确保作者只能操作自己的文章。

方案与当前项目实际情况紧密结合，对接已有的 `article_authors` 表、`sys_user_role` 表、`ResourceServerConfig` 配置，并为后续阶段四（博客端 GUEST 角色落地）和阶段五（前端多角色菜单适配）奠定基础。
