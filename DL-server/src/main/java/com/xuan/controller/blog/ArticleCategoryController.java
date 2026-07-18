package com.xuan.controller.blog;


import com.xuan.entity.ArticleCategories;
import com.xuan.result.Result;
import com.xuan.service.IArticleCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端文章分类接口
 */
@RestController("blogArticleCategoryController")
@RequestMapping("/blog/articleCategory")
@Slf4j
@RequiredArgsConstructor
public class ArticleCategoryController {

    private final IArticleCategoryService articleCategoryService;

    /**
     * 获取所有可见文章分类（有已发布文章的分类）
     */
    @GetMapping
    public Result<List<ArticleCategories>> getVisibleCategories() {
        log.info("博客端获取可见文章分类");
        List<ArticleCategories> categoryList = articleCategoryService.getVisibleCategories();
        return Result.success(categoryList);
    }
}
