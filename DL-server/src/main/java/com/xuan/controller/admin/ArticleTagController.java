package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.ArticleTagDTO;
import com.xuan.entity.ArticleTags;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.IArticleTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章标签接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（标签管理属于系统级配置，
 * AUTHOR 只能选用已有标签，不能增删改标签）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@Slf4j
@RestController("adminArticleTagController")
@RequestMapping("/admin/article/tag")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class ArticleTagController {

    private final IArticleTagService articleTagService;

    /**
     * 获取所有标签
     * @return
     */
    @GetMapping
    public Result<List<ArticleTags>> listAll() {
        List<ArticleTags> list = articleTagService.listAll();
        return Result.success(list);
    }

    /**
     * 添加标签
     * @param articleTagDTO
     * @return
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "articleTag")
    public Result addTag(@Valid @RequestBody ArticleTagDTO articleTagDTO) {
        log.info("添加文章标签: {}", articleTagDTO);
        articleTagService.addTag(articleTagDTO);
        return Result.success();
    }

    /**
     * 修改标签
     * @param articleTagDTO
     * @return
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "articleTag", targetId = "#articleTagDTO.id")
    public Result updateTag(@Valid @RequestBody ArticleTagDTO articleTagDTO) {
        log.info("修改文章标签: {}", articleTagDTO);
        articleTagService.updateTag(articleTagDTO);
        return Result.success();
    }

    /**
     * 批量删除标签
     * @param ids
     * @return
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "articleTag", targetId = "#ids")
    public Result batchDeleteTags(@RequestParam List<Long> ids) {
        log.info("批量删除文章标签：{}", ids);
        articleTagService.batchDeleteTags(ids);
        return Result.success();
    }
}
