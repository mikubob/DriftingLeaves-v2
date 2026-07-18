package com.xuan.service.impl;

import com.xuan.entity.SysUser;
import com.xuan.mapper.ArticleAuthorsMapper;
import com.xuan.mapper.ArticleCommentMapper;
import com.xuan.mapper.SysUserMapper;
import com.xuan.service.ArticlePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章权限校验服务实现
 * <p>
 * 通过 article_authors 表（文章-作者多对多关联）判断当前用户与文章的归属关系，
 * 为 AUTHOR 角色提供数据范围校验能力。
 * </p>
 *
 * <h3>调用链路</h3>
 * <pre>
 * Controller @PreAuthorize SpEL
 *   └─ @articlePermissionService.isAuthor(articleId, authentication.name)
 *        └─ SysUserMapper.selectByUsername(username) → userId
 *        └─ ArticleAuthorsMapper.existsByArticleIdAndUserId(articleId, userId) → true/false
 * </pre>
 *
 * <h3>性能说明</h3>
 * <ul>
 *     <li>每次校验需要 2 次 DB 查询：先查 sys_user 取 userId，再查 article_authors 判断归属</li>
 *     <li>由于 SpEL 短路求值，ADMIN 角色不会触发本服务的调用，性能开销仅在 AUTHOR 角色时产生</li>
 *     <li>后续可考虑增加 Redis 缓存，缓存 username → userId 的映射关系</li>
 * </ul>
 *
 * @see ArticlePermissionService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticlePermissionServiceImpl implements ArticlePermissionService {

    /**
     * 文章-作者关联 Mapper：用于查询 article_authors 表
     */
    private final ArticleAuthorsMapper articleAuthorsMapper;

    /**
     * 文章评论 Mapper：用于根据 commentId 查询所属 articleId
     */
    private final ArticleCommentMapper articleCommentMapper;

    /**
     * 系统用户 Mapper：用于根据 username 查询 userId
     */
    private final SysUserMapper sysUserMapper;

    /**
     * 判断当前用户是否为指定文章的作者（含共同作者）
     * <p>
     * 调用 article_authors 表查询：article_id + user_id + invite_status=1。
     * </p>
     */
    @Override
    public boolean isAuthor(Long articleId, String username) {
        // 入参校验：null 直接返回 false，避免 SpEL 求值抛 NPE
        if (articleId == null || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            log.warn("权限校验失败：用户不存在, username={}", username);
            return false;
        }
        // 查询 article_authors 表，判断用户是否为该文章的作者（任意角色，已接受邀请）
        return articleAuthorsMapper.existsByArticleIdAndUserId(articleId, userId);
    }

    /**
     * 判断当前用户是否为指定文章的第一作者
     * <p>
     * 调用 article_authors 表查询：article_id + user_id + author_role=1 + invite_status=1。
     * </p>
     */
    @Override
    public boolean isFirstAuthor(Long articleId, String username) {
        if (articleId == null || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            log.warn("权限校验失败：用户不存在, username={}", username);
            return false;
        }
        // 查询 article_authors 表，判断用户是否为该文章的第一作者
        return articleAuthorsMapper.existsFirstAuthorByArticleIdAndUserId(articleId, userId);
    }

    /**
     * 批量判断：当前用户是否为所有指定文章的第一作者
     * <p>
     * 通过 count 查询实现：统计 articleIds 中属于当前用户作为第一作者的文章数量，
     * 若数量等于 articleIds.size()，则说明全部为第一作者。
     * </p>
     */
    @Override
    public boolean isFirstAuthor(List<Long> articleIds, String username) {
        if (articleIds == null || articleIds.isEmpty() || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            log.warn("权限校验失败：用户不存在, username={}", username);
            return false;
        }
        // 批量统计：当前用户作为第一作者的文章数
        Long count = articleAuthorsMapper.countFirstAuthorByArticleIdsAndUserId(articleIds, userId);
        // 全部为第一作者才返回 true
        return count != null && count == articleIds.size();
    }

    /**
     * 判断指定评论是否在当前用户自己的文章下
     * <p>
     * 两步查询：
     * <ol>
     *     <li>通过 commentId 查询 article_comments 表，获取所属 articleId</li>
     *     <li>调用 {@link #isAuthor(Long, String)} 判断该文章是否属于当前用户</li>
     * </ol>
     * </p>
     */
    @Override
    public boolean isCommentInOwnArticle(Long commentId, String username) {
        if (commentId == null || username == null) {
            return false;
        }
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            log.warn("权限校验失败：用户不存在, username={}", username);
            return false;
        }
        // 第一步：根据评论ID查询所属文章ID
        Long articleId = articleCommentMapper.selectArticleIdByCommentId(commentId);
        if (articleId == null) {
            log.warn("权限校验失败：评论不存在, commentId={}", commentId);
            return false;
        }
        // 第二步：判断该文章是否属于当前用户
        return articleAuthorsMapper.existsByArticleIdAndUserId(articleId, userId);
    }

    /**
     * 批量判断：指定评论是否全部在当前用户自己的文章下
     * <p>
     * 逐个调用 {@link #isCommentInOwnArticle(Long, String)} 判断。
     * 评论批量操作的数据量通常较小（一般 ≤ 50），逐个查询可接受；
     * 后续如有性能需求可改为一次 SQL 联表查询。
     * </p>
     */
    @Override
    public boolean areCommentsInOwnArticle(List<Long> commentIds, String username) {
        if (commentIds == null || commentIds.isEmpty() || username == null) {
            return false;
        }
        // 逐个判断：任意一个不在自己文章下则返回 false
        for (Long commentId : commentIds) {
            if (!isCommentInOwnArticle(commentId, username)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 根据用户名查询用户ID
     * <p>
     * 内部辅助方法，避免重复代码。
     * 用户不存在时返回 null（调用方需自行处理）。
     * </p>
     *
     * @param username 用户名
     * @return 用户ID；用户不存在返回 null
     */
    private Long getUserIdByUsername(String username) {
        SysUser user = sysUserMapper.selectByUsername(username);
        return user != null ? user.getId() : null;
    }
}
