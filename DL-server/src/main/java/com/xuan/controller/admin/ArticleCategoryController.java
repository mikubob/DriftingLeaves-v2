package com.xuan.controller.admin;

import com.xuan.annotation.OperationLog;
import com.xuan.dto.ArticleCategoryDTO;
import com.xuan.entity.ArticleCategories;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.IArticleCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章分类接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（分类管理属于系统级配置，
 * AUTHOR 只能选用已有分类，不能增删改分类）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@Slf4j
@RestController("adminArticleCategoryController")
@RequestMapping("/admin/articleCategory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class ArticleCategoryController {

    private final IArticleCategoryService articleCategoryService;

    /**
     * 获取所有文章分类
     * @return
     */
    @GetMapping
    public Result<List<ArticleCategories>> listAll() {
        List<ArticleCategories> categoryList = articleCategoryService.listAll();
        return Result.success(categoryList);
    }

    /**
     * 添加文章分类
     * @param articleCategoryDTO
     * @return
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "articleCategory")
    public Result addCategory(@Valid @RequestBody ArticleCategoryDTO articleCategoryDTO) {
        log.info("添加文章分类,{}", articleCategoryDTO);
        articleCategoryService.addCategory(articleCategoryDTO);
        return Result.success();
    }

    /**
     * 更新文章分类
     * @param articleCategoryDTO
     * @return
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "articleCategory", targetId = "#articleCategoryDTO.id")
    public Result updateCategory(@Valid @RequestBody ArticleCategoryDTO articleCategoryDTO) {
        log.info("更新文章分类,{}", articleCategoryDTO);
        articleCategoryService.updateCategory(articleCategoryDTO);
        return Result.success();
    }

    /**
     * 批量删除文章分类
     * @param ids
     * @return
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "articleCategory", targetId = "#ids")
    public Result deleteCategory(@RequestParam List<Long> ids) {
        log.info("批量删除文章分类,{}", ids);
        articleCategoryService.batchDelete(ids);
        return Result.success();
    }
}
