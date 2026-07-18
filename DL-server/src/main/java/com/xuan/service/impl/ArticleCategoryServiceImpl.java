package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.MessageConstant;
import com.xuan.dto.ArticleCategoryDTO;
import com.xuan.entity.ArticleCategories;
import com.xuan.entity.Articles;
import com.xuan.exception.ArticleCategoryException;
import com.xuan.mapper.ArticleCategoryMapper;
import com.xuan.service.IArticleCategoryService;
import com.xuan.service.IArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章分类服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCategoryServiceImpl extends ServiceImpl<ArticleCategoryMapper, ArticleCategories> implements IArticleCategoryService {

    private final IArticleService articleService;

    /**
     * 获取所有文章分类
     *
     * @return 文章分类列表
     */
    @Override
    @Cacheable(value = "articleCategories", key = "'all'")
    public List<ArticleCategories> listAll() {
        // 构建查询条件
        LambdaQueryWrapper<ArticleCategories> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ArticleCategories::getSort)
                .orderByDesc(ArticleCategories::getId);
        // 执行查询
        return list(wrapper);
    }

    /**
     * 添加文章分类
     *
     * @param articleCategoryDTO 文章分类数据
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "articleCategories", allEntries = true),
            @CacheEvict(value = "articleList", allEntries = true),
            @CacheEvict(value = "blogReport", allEntries = true)
    })
    public void addCategory(ArticleCategoryDTO articleCategoryDTO) {
        //1.复制属性
        ArticleCategories articleCategories = BeanUtil.copyProperties(articleCategoryDTO, ArticleCategories.class);
        //2.保存分类
        save(articleCategories);
    }

    /**
     * 更新文章分类
     *
     * @param articleCategoryDTO 文章分类数据
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "articleCategories", allEntries = true),
            @CacheEvict(value = "articleList", allEntries = true),
            @CacheEvict(value = "blogReport", allEntries = true)
    })
    public void updateCategory(ArticleCategoryDTO articleCategoryDTO) {
        //1.根据 ID 查询分类
        ArticleCategories articleCategories = getById(articleCategoryDTO.getId());
        if (articleCategories == null) {
            throw new ArticleCategoryException(MessageConstant.CATEGORY_NOT_FOUND);
        }
        
        //2.复制属性
        BeanUtil.copyProperties(articleCategoryDTO, articleCategories);
        //3.更新分类
        updateById(articleCategories);
    }

    /**
     * 批量删除文章分类
     *
     * @param ids 文章分类 id 列表
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "articleCategories", allEntries = true),
            @CacheEvict(value = "articleList", allEntries = true),
            @CacheEvict(value = "blogReport", allEntries = true)
    })
    public void batchDelete(List<Long> ids) {
        //1.检查分类下是否有关联文章
        for (Long id : ids) {
            long count = articleService.count(new LambdaQueryWrapper<Articles>()
                    .eq(Articles::getCategoryId, id));
            if (count > 0) {
                throw new ArticleCategoryException(MessageConstant.CATEGORY_HAS_ARTICLES);
            }
        }
        //2.批量删除分类
        removeByIds(ids);
    }

    /**
     * 获取所有有已发布文章的可见分类
     *
     * @return 可见分类列表
     */
    @Override
    @Cacheable(value = "articleCategories", key = "'visible'")
    public List<ArticleCategories> getVisibleCategories() {
        // 执行自定义 SQL 查询
        return baseMapper.getVisibleCategories();
    }

    /**
     * 获取文章分类总数
     * @return 文章分类总数
     */
    @Override
    public Integer countTotal() {
        return Math.toIntExact(count());
    }
}
