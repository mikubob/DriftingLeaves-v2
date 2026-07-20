package com.xuan.service;

import java.util.List;

/**
 * 文章权限校验服务
 * <p>
 * RBAC 权限模型的核心组件之一，专门为 AUTHOR 角色提供数据范围校验能力。
 * </p>
 * <p>
 * 使用场景：在 Controller 方法级 @PreAuthorize 注解中通过 SpEL 表达式调用，
 * 用于校验当前 AUTHOR 用户是否只能操作自己的文章 / 自己文章下的评论。
 * </p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * @PutMapping
 * @PreAuthorize("hasRole('ADMIN') or " +
 *               "(hasRole('AUTHOR') and @articlePermissionService.isAuthor(#articleDTO.id, authentication.name))")
 * public Result updateArticle(@RequestBody ArticleDTO articleDTO) { ... }
 * }</pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *     <li>SpEL 表达式中的 {@code or} 是短路求值，{@code hasRole('ADMIN')} 为 true 时不会调用本服务，避免不必要的数据库查询</li>
 *     <li>本服务仅承担"数据范围校验"职责，"角色判断"由 Spring Security 的 hasRole 系列函数完成</li>
 *     <li>所有方法对 null 入参返回 false，避免 SpEL 求值时抛出 NPE</li>
 * </ul>
 *
 * @see com.xuan.service.impl.ArticlePermissionServiceImpl
 */
public interface ArticlePermissionService {

    /**
     * 判断当前用户是否为指定文章的作者（含共同作者）
     * <p>
     * 用于"编辑文章"、"更新状态"等场景：第一作者、共同作者均可操作。
     * </p>
     *
     * @param articleId 文章ID
     * @param username  当前用户名（来自 authentication.name，即 JWT 的 sub claim）
     * @return true=是作者（任意角色，且邀请已接受）；false=非作者 / 文章不存在 / 入参为 null
     */
    boolean isAuthor(Long articleId, String username);

    /**
     * 判断当前用户是否为指定文章的第一作者
     * <p>
     * 用于"删除文章"、"提交审核"等高权限场景：仅第一作者可执行。
     * </p>
     *
     * @param articleId 文章ID
     * @param username  当前用户名
     * @return true=是第一作者（author_role=1 且 invite_status=1）；false=非第一作者 / 入参为 null
     */
    boolean isFirstAuthor(Long articleId, String username);

    /**
     * 批量判断：当前用户是否为所有指定文章的第一作者
     * <p>
     * 用于"批量删除文章"场景：所有 articleId 都必须是当前用户作为第一作者的文章，才允许批量删除。
     * </p>
     *
     * @param articleIds 文章ID列表
     * @param username   当前用户名
     * @return true=全部为第一作者；false=存在非第一作者的文章 / 列表为空 / 入参为 null
     */
    boolean isFirstAuthor(List<Long> articleIds, String username);

    /**
     * 判断指定评论是否在当前用户自己的文章下
     * <p>
     * 用于"回复评论"场景：AUTHOR 只能回复自己文章下的评论。
     * </p>
     * <p>
     * 实现思路：先通过 commentId 查出 articleId，再调用 {@link #isAuthor(Long, String)} 判断。
     * </p>
     *
     * @param commentId 评论ID
     * @param username  当前用户名
     * @return true=评论在自己的文章下；false=评论不存在 / 不在自己文章下 / 入参为 null
     */
    boolean isCommentInOwnArticle(Long commentId, String username);

    /**
     * 批量判断：指定评论是否全部在当前用户自己的文章下
     * <p>
     * 用于"批量审核评论"、"批量删除评论"场景：所有评论必须都在 AUTHOR 自己的文章下才允许操作。
     * </p>
     *
     * @param commentIds 评论ID列表
     * @param username   当前用户名
     * @return true=全部在自己文章下；false=存在非自己文章的评论 / 列表为空 / 入参为 null
     */
    boolean areCommentsInOwnArticle(List<Long> commentIds, String username);
}
