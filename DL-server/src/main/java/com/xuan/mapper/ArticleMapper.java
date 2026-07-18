package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xuan.dto.ArticlePageQueryDTO;
import com.xuan.dto.ArticleTitleViewCountDTO;
import com.xuan.entity.Articles;
import com.xuan.vo.ArticleArchiveItemVO;
import com.xuan.vo.ArticleVO;
import com.xuan.vo.BlogArticleDetailVO;
import com.xuan.vo.BlogArticleVO;
import com.xuan.vo.HotArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Articles> {

    /**
     * 管理端分页查询文章列表。
     * <p>
     * 管理端列表需要展示分类名称，单纯使用 BaseMapper 分页只能查询 articles 表字段，
     * 因此这里通过自定义 SQL 关联 article_categories 表补齐 categoryName。
     *
     * @param page 分页对象
     * @param dto 查询条件
     * @return 带分类名称的文章分页结果
     */
    IPage<ArticleVO> pageQueryWithCategory(IPage<ArticleVO> page, @Param("dto") ArticlePageQueryDTO dto);
    
    /**
     * 全文搜索文章（使用 MySQL FULLTEXT 索引）
     * @param page 分页对象
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    IPage<ArticleVO> searchWithFullText(IPage<ArticleVO> page, @Param("keyword") String keyword);
    
    /**
     * 获取已发布文章分页列表
     * @param page 分页对象
     * @return 分页结果
     */
    IPage<BlogArticleVO> getPublishedPage(IPage<BlogArticleVO> page);
    
    /**
     * 根据 slug 获取文章详情
     * @param slug 文章 slug
     * @return 文章详情 VO
     */
    BlogArticleDetailVO getBySlug(@Param("slug") String slug);
    
    /**
     * 获取上一篇文章（按发布时间）
     * @param id 当前文章 ID
     * @return 上一篇文章 VO
     */
    BlogArticleVO getPrevArticle(@Param("id") Long id);
    
    /**
     * 获取下一篇文章（按发布时间）
     * @param id 当前文章 ID
     * @return 下一篇文章 VO
     */
    BlogArticleVO getNextArticle(@Param("id") Long id);
    
    /**
     * 获取相关文章推荐（同分类，排除当前文章）
     * @param articleId 当前文章 ID
     * @param categoryId 分类 ID
     * @return 相关文章列表
     */
    List<BlogArticleVO> getRelatedArticles(@Param("articleId") Long articleId, @Param("categoryId") Long categoryId);
    
    /**
     * 获取文章归档列表
     * @return 文章归档列表
     */
    List<ArticleArchiveItemVO> getArchiveList();
    
    /**
     * 博客端文章搜索（仅获取已发布文章）
     * @param page 分页对象
     * @param keyword 搜索关键词
     * @return 分页结果
     */
    IPage<BlogArticleVO> searchPublished(IPage<BlogArticleVO> page, @Param("keyword") String keyword);

    /**
     * 根据分类 ID 获取已发布文章分页列表
     * @param page 分页对象
     * @param categoryId 分类 ID
     * @return 分页结果
     */
    IPage<BlogArticleVO> getPublishedByCategoryId(IPage<BlogArticleVO> page, @Param("categoryId") Long categoryId);

    /**
     * 根据标签 ID 获取已发布文章分页列表
     * @param page 分页对象
     * @param tagId 标签 ID
     * @return 分页结果
     */
    IPage<BlogArticleVO> getPublishedByTagId(IPage<BlogArticleVO> page, @Param("tagId") Long tagId);

    void addViewCount(@Param("id") Long id, @Param("increment") int increment);

    void addLikeCount(@Param("id") Long id, @Param("increment") int increment);

    /**
     * 获取文章浏览量 top10
     * @return 文章浏览量 top10 列表
     */
    List<ArticleTitleViewCountDTO> getViewTop10();

    /**
     * 获取本月热门文章点赞榜（前 5 篇）
     * @param begin 本月开始时间
     * @param end 下月开始时间
     * @return 本月点赞数最高的已发布文章列表
     */
    List<HotArticleVO> getMonthHotArticlesByLike(@Param("begin") LocalDateTime begin,
                                                 @Param("end") LocalDateTime end);

    /**
     * 获取本月热门文章浏览榜（前 5 篇）
     * @param begin 本月开始时间
     * @param end 下月开始时间
     * @return 本月浏览量最高的已发布文章列表
     */
    List<HotArticleVO> getMonthHotArticlesByView(@Param("begin") LocalDateTime begin,
                                                 @Param("end") LocalDateTime end);

    /**
     * 获取全站热门文章点赞榜（前 5 篇）
     * @return 全站总点赞数最高的已发布文章列表
     */
    List<HotArticleVO> getSiteHotArticlesByLike();

    /**
     * 获取全站热门文章浏览榜（前 5 篇）
     * @return 全站总浏览量最高的已发布文章列表
     */
    List<HotArticleVO> getSiteHotArticlesByView();
}
