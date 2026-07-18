package com.xuan.controller.admin;

import com.xuan.annotation.OperationLog;
import com.xuan.dto.ArticleCommentPageQueryDTO;
import com.xuan.dto.ArticleCommentReplyDTO;
import com.xuan.entity.ArticleComments;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleCommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章评论接口
 */
@Slf4j
@RestController("adminArticleCommentController")
@RequestMapping("/admin/article/comment")
@RequiredArgsConstructor
public class ArticleCommentController {

    private final IArticleCommentService articleCommentService;

    /**
     * 分页条件查询评论（时间、是否审核）
     * @param articleCommentPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> pageQuery(ArticleCommentPageQueryDTO articleCommentPageQueryDTO) {
        log.info("分页条件查询文章评论: {}", articleCommentPageQueryDTO);
        PageResult pageResult = articleCommentService.pageQuery(articleCommentPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据文章ID查询评论
     * @param articleId
     * @return
     */
    @GetMapping("/{articleId}")
    public Result<List<ArticleComments>> getByArticleId(@PathVariable Long articleId) {
        log.info("根据文章ID查询评论: articleId={}", articleId);
        List<ArticleComments> comments = articleCommentService.getByArticleId(articleId);
        return Result.success(comments);
    }

    /**
     * 批量审核通过评论
     * @param ids
     * @return
     */
    @PutMapping("/approve")
    @OperationLog(value = OperationType.UPDATE, target = "articleComment", targetId = "#ids")
    public Result<String> batchApprove(@RequestParam List<Long> ids) {
        log.info("批量审核通过文章评论: {}", ids);
        articleCommentService.batchApprove(ids);
        return Result.success();
    }

    /**
     * 批量删除评论
     * @param ids
     * @return
     */
    @DeleteMapping
    @OperationLog(value = OperationType.DELETE, target = "articleComment", targetId = "#ids")
    public Result<String> batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除文章评论: {}", ids);
        articleCommentService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 管理员回复评论
     * @param articleCommentReplyDTO
     * @return
     */
    @PostMapping("/reply")
    @OperationLog(value = OperationType.INSERT, target = "articleComment", targetId = "#articleCommentReplyDTO.parentId")
    public Result<String> adminReply(@Valid @RequestBody ArticleCommentReplyDTO articleCommentReplyDTO,
                                     HttpServletRequest request) {
        log.info("管理员回复文章评论: {}", articleCommentReplyDTO);
        articleCommentService.adminReply(articleCommentReplyDTO, request);
        return Result.success();
    }
}
