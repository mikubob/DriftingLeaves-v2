package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.ArticleCategories;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ArticleCategoryMapper extends BaseMapper<ArticleCategories> {

    /**
     * 获取所有分类及其已发布文章数（包括没有文章的分类）
     */
    List<ArticleCategories> getVisibleCategories();
}
