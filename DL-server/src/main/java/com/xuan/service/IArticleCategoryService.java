package com.xuan.service;



import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.ArticleCategoryDTO;
import com.xuan.entity.ArticleCategories;

import java.util.List;

public interface IArticleCategoryService extends IService<ArticleCategories> {
    /**
     * 获取所有文章分类
     * @return
     */
    List<ArticleCategories> listAll();

    /**
     * 添加文章分类
     * @param articleCategoryDTO
     */
    void addCategory(ArticleCategoryDTO articleCategoryDTO);

    /**
     * 更新文章分类（含排序）
     * @param articleCategoryDTO
     */
    void updateCategory(ArticleCategoryDTO articleCategoryDTO);

    /**
     * 批量删除文章分类
     * @param ids
     */
    void batchDelete(List<Long> ids);

    // ===== 博客端方法 =====

    /**
     * 获取所有有已发布文章的可见分类
     */
    List<ArticleCategories> getVisibleCategories();

    /**
     * 获取文章分类总数
     * @return 文章分类总数
     */
    Integer countTotal();
}
