package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.ArticleDTO;
import com.xuan.dto.ArticlePageQueryDTO;
import com.xuan.entity.Articles;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.vo.ArticleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章接口
 */
@Slf4j
@RestController("adminArticleController")
@RequestMapping("/admin/article")
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articleService;

    /**
     * 分页条件查询文章列表
     * @param articlePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult<ArticleVO>> pageQuery(ArticlePageQueryDTO articlePageQueryDTO) {
        log.info("分页条件查询文章列表: {}", articlePageQueryDTO);
        // 返回 ArticleVO，确保管理列表可以直接拿到 categoryName 等展示字段。
        PageResult<ArticleVO> pageResult = articleService.pageQuery(articlePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据 ID 获取文章详情
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<Articles> getArticleById(@PathVariable Long id) {
        log.info("根据 ID 获取文章详情：{}", id);
        Articles articles = articleService.getArticleById(id);
        return Result.success(articles);
    }

    /**
     * 创建文章
     * @param articleDTO
     * @return
     */
    @PostMapping
    @OperationLog(value = OperationType.INSERT, target = "article")
    public Result createArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        log.info("创建文章: {}", articleDTO);
        articleService.createArticle(articleDTO);
        return Result.success();
    }

    /**
     * 更新文章
     * @param articleDTO
     * @return
     */
    @PutMapping
    @OperationLog(value = OperationType.UPDATE, target = "article", targetId = "#articleDTO.id")
    public Result updateArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        log.info("更新文章: {}", articleDTO);
        articleService.updateArticle(articleDTO);
        return Result.success();
    }

    /**
     * 批量删除文章
     * @param ids
     * @return
     */
    @DeleteMapping
    @OperationLog(value = OperationType.DELETE, target = "article", targetId = "#ids")
    public Result batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除文章: {}", ids);
        articleService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 更新文章状态
     * @param id 文章ID
     * @param status 文章状态：0草稿 1待审核 2已发布 3违规
     * @return
     */
    @PutMapping("/status/{id}")
    @OperationLog(value = OperationType.UPDATE, target = "article", targetId = "#id")
    public Result updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        log.info("更新文章状态: id={}, status={}", id, status);
        articleService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 置顶/取消置顶文章
     * @param id
     * @param isTop 0-取消置顶，1-置顶
     * @return
     */
    @PutMapping("/top/{id}")
    @OperationLog(value = OperationType.UPDATE, target = "article", targetId = "#id")
    public Result toggleTop(@PathVariable Long id, @RequestParam Integer isTop) {
        log.info("置顶/取消置顶文章: id={}, isTop={}", id, isTop);
        articleService.toggleTop(id, isTop);
        return Result.success();
    }

    /**
     * 文章搜索（全文本搜索：标题、摘要、内容）
     * @param keyword
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/search")
    public Result<PageResult> search(@RequestParam String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        log.info("文章搜索：keyword={}", keyword);
        PageResult pageResult = articleService.search(keyword, page, pageSize);
        return Result.success(pageResult);
    }
}
