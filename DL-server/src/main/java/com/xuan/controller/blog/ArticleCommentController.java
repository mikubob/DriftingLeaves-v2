package com.xuan.controller.blog;


import com.xuan.annotation.RateLimit;
import com.xuan.dto.ArticleCommentDTO;
import com.xuan.dto.ArticleCommentEditDTO;
import com.xuan.result.Result;
import com.xuan.service.IArticleCommentService;
import com.xuan.vo.ArticleCommentVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 博客端文章评论接口
 */
@RestController("blogArticleCommentController")
@RequestMapping("/blog/articleComment")
@Slf4j
@RequiredArgsConstructor
public class ArticleCommentController {

    private final IArticleCommentService articleCommentService;

    /**
     * 根据文章ID获取评论列表（树形结构，含当前用户的未审核评论）
     */
    @GetMapping("/article/{articleId}")
    public Result<List<ArticleCommentVO>> getCommentTree(@PathVariable Long articleId,
                                                         @RequestParam(required = false) Long userId) {
        log.info("博客端获取文章评论树: articleId={}, userId={}", articleId, userId);
        List<ArticleCommentVO> commentTree = articleCommentService.getCommentTree(articleId, userId);
        return Result.success(commentTree);
    }

    /**
     * 提交评论（添加评论/回复评论）
     */
    @PostMapping
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
              timeWindow = 60, message = "评论过于频繁，请稍后再试")
    public Result<String> submitComment(@Valid @RequestBody ArticleCommentDTO articleCommentDTO,
                                        HttpServletRequest request) {
        log.info("用户提交文章评论: {}", articleCommentDTO);
        articleCommentService.submitComment(articleCommentDTO, request);
        return Result.success();
    }

    /**
     * 用户编辑评论
     */
    @PutMapping("/edit")
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
              timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result<String> editComment(@Valid @RequestBody ArticleCommentEditDTO editDTO) {
        log.info("用户编辑评论: {}", editDTO);
        articleCommentService.editComment(editDTO);
        return Result.success();
    }

    /**
     * 用户删除评论
     */
    @DeleteMapping("/{id}")
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
              timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result<String> deleteComment(@PathVariable Long id, @RequestParam Long userId) {
        log.info("用户删除评论: id={}, userId={}", id, userId);
        articleCommentService.visitorDeleteComment(id, userId);
        return Result.success();
    }
}
