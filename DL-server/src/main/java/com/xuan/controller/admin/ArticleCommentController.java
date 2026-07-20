package com.xuan.controller.admin;

import com.xuan.annotation.OperationLog;
import com.xuan.dto.ArticleCommentPageQueryDTO;
import com.xuan.dto.ArticleCommentReplyDTO;
import com.xuan.entity.ArticleComments;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ArticlePermissionService;
import com.xuan.service.IArticleCommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章评论接口
 * <p>
 * RBAC 权限模型第三层（方法级 @PreAuthorize）落地。
 * </p>
 *
 * <h3>权限策略</h3>
 * <ul>
 *     <li>读操作（GET）：ADMIN + AUTHOR + AUDITOR 均可查询</li>
 *     <li>写操作（POST/PUT/DELETE）：ADMIN 或（AUTHOR 且评论在自己文章下）</li>
 *     <li>AUDITOR：仅读，无写权限</li>
 * </ul>
 *
 * <h3>数据范围校验</h3>
 * <p>
 * AUTHOR 操作评论时，需通过 {@link ArticlePermissionService} 校验评论所在文章是否属于当前用户。
 * 由于评论操作通常批量进行，使用 {@link ArticlePermissionService#areCommentsInOwnArticle(List, String)} 批量校验。
 * </p>
 */
@Slf4j
@RestController("adminArticleCommentController")
@RequestMapping("/admin/article/comment")
@RequiredArgsConstructor
public class ArticleCommentController {

    private final IArticleCommentService articleCommentService;

    /**
     * 文章权限校验服务
     * <p>
     * 用于在 @PreAuthorize SpEL 中校验 AUTHOR 是否只能操作自己文章下的评论。
     * </p>
     */
    private final ArticlePermissionService articlePermissionService;

    /**
     * 分页条件查询评论（时间、是否审核）
     * <p>
     * 权限：ADMIN + AUTHOR + AUDITOR 均可查询。
     * AUTHOR 在 Service 层仅返回自己文章下的评论。
     * </p>
     * @param articleCommentPageQueryDTO 查询条件
     * @return 评论分页列表
     */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<PageResult> pageQuery(ArticleCommentPageQueryDTO articleCommentPageQueryDTO) {
        log.info("分页条件查询文章评论: {}", articleCommentPageQueryDTO);
        PageResult pageResult = articleCommentService.pageQuery(articleCommentPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据文章ID查询评论
     * <p>
     * 权限：ADMIN + AUTHOR + AUDITOR 均可查询。
     * AUTHOR 在 Service 层校验文章归属（非自己文章返回 403 或空）。
     * </p>
     * @param articleId 文章ID
     * @return 评论列表
     */
    @GetMapping("/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<List<ArticleComments>> getByArticleId(@PathVariable Long articleId) {
        log.info("根据文章ID查询评论: articleId={}", articleId);
        List<ArticleComments> comments = articleCommentService.getByArticleId(articleId);
        return Result.success(comments);
    }

    /**
     * 批量审核通过评论
     * <p>
     * 权限：ADMIN 或（AUTHOR 且所有评论都在自己文章下）。
     * SpEL 表达式调用 {@link ArticlePermissionService#areCommentsInOwnArticle(List, String)} 批量校验。
     * </p>
     * <p>
     * 短路求值说明：ADMIN 直接通过，不调用 ArticlePermissionService；
     * AUTHOR 才调用批量校验，任意一条评论不在自己文章下则返回 403。
     * </p>
     * @param ids 评论ID列表
     * @return 操作结果
     */
    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN') or " +
                  "(hasRole('AUTHOR') and @articlePermissionService.areCommentsInOwnArticle(#ids, authentication.name))")
    @OperationLog(value = OperationType.UPDATE, target = "articleComment", targetId = "#ids")
    public Result<String> batchApprove(@RequestParam List<Long> ids) {
        log.info("批量审核通过文章评论: {}", ids);
        articleCommentService.batchApprove(ids);
        return Result.success();
    }

    /**
     * 批量删除评论
     * <p>
     * 权限：ADMIN 或（AUTHOR 且所有评论都在自己文章下）。
     * 与 {@link #batchApprove} 相同的批量校验逻辑。
     * </p>
     * @param ids 评论ID列表
     * @return 操作结果
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or " +
                  "(hasRole('AUTHOR') and @articlePermissionService.areCommentsInOwnArticle(#ids, authentication.name))")
    @OperationLog(value = OperationType.DELETE, target = "articleComment", targetId = "#ids")
    public Result<String> batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除文章评论: {}", ids);
        articleCommentService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 管理员/作者回复评论
     * <p>
     * 权限：ADMIN 或（AUTHOR 且该评论在自己文章下）。
     * SpEL 表达式调用 {@link ArticlePermissionService#isCommentInOwnArticle(Long, String)} 校验。
     * </p>
     * <p>
     * SpEL 表达式中使用 #articleCommentReplyDTO.parentId 作为评论ID：
     * 因为 ArticleCommentReplyDTO 的 parentId 字段表示被回复的评论ID，
     * 通过该ID校验该评论是否在 AUTHOR 自己的文章下。
     * </p>
     * @param articleCommentReplyDTO 回复数据
     * @param request HTTP 请求（用于获取 IP 等信息）
     * @return 操作结果
     */
    @PostMapping("/reply")
    @PreAuthorize("hasRole('ADMIN') or " +
                  "(hasRole('AUTHOR') and @articlePermissionService.isCommentInOwnArticle(#articleCommentReplyDTO.parentId, authentication.name))")
    @OperationLog(value = OperationType.INSERT, target = "articleComment", targetId = "#articleCommentReplyDTO.parentId")
    public Result<String> adminReply(@Valid @RequestBody ArticleCommentReplyDTO articleCommentReplyDTO,
                                     HttpServletRequest request) {
        log.info("管理员回复文章评论: {}", articleCommentReplyDTO);
        articleCommentService.adminReply(articleCommentReplyDTO, request);
        return Result.success();
    }
}
