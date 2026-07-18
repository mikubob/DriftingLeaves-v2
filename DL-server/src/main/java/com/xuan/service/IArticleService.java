package com.xuan.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.ArticleDTO;
import com.xuan.dto.ArticlePageQueryDTO;
import com.xuan.dto.ArticleTitleViewCountDTO;
import com.xuan.entity.Articles;
import com.xuan.result.PageResult;
import com.xuan.vo.ArticleArchiveVO;
import com.xuan.vo.ArticleVO;
import com.xuan.vo.BlogArticleDetailVO;
import com.xuan.vo.BlogArticleVO;
import com.xuan.vo.HotArticleVO;

import java.util.List;

/**
 * 文章服务
 */
public interface IArticleService extends IService<Articles> {

    /**
     * 创建文章
     * @param articleDTO
     */
    void createArticle(ArticleDTO articleDTO);

    /**
     * 分页条件查询文章列表（含草稿）
     * @param articlePageQueryDTO
     * @return
     */
    PageResult<ArticleVO> pageQuery(ArticlePageQueryDTO articlePageQueryDTO);

    /**
     * 根据ID获取文章详情
     * @param id
     * @return
     */
    Articles getArticleById(Long id);

    /**
     * 更新文章
     * @param articleDTO
     */
    void updateArticle(ArticleDTO articleDTO);

    /**
     * 批量删除文章
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 更新文章状态
     * @param id 文章ID
     * @param status 文章状态：0草稿 1待审核 2已发布 3违规
     */
    void updateStatus(Long id, Integer status);

    /**
     * 置顶/取消置顶文章
     * @param id
     * @param isTop
     */
    void toggleTop(Long id, Integer isTop);

    /**
     * 文章搜索（标题、摘要、内容的全文本搜索）
     * @param keyword
     * @param page
     * @param pageSize
     * @return
     */
    PageResult<ArticleVO> search(String keyword, int page, int pageSize);

    // ===== 博客端方法 =====

    /**
     * 获取已发布文章列表（分页）
     */
    PageResult<BlogArticleVO> getPublishedPage(int page, int pageSize);

    /**
     * 根据slug获取文章详情（浏览量+1）
     */
    BlogArticleDetailVO getBySlug(String slug);

    /**
     * 文章浏览量+1（写入Redis，基于文章ID）
     */
    void incrementViewCount(Long articleId);

    /**
     * 根据分类ID获取已发布文章列表（分页）
     */
    PageResult<BlogArticleVO> getPublishedByCategoryId(Long categoryId, int page, int pageSize);

    /**
     * 获取文章归档（按年月分组）
     */
    List<ArticleArchiveVO> getArchive();

    /**
     * 博客端文章搜索（仅已发布）
     */
    PageResult<BlogArticleVO> searchPublished(String keyword, int page, int pageSize);

    /**
     * 根据标签ID获取已发布文章列表
     */
    PageResult getPublishedByTagId(Long tagId, int page, int pageSize);

    /**
     * 获取已发布文章总数
     * @return 已发布文章总数
     */
    Integer countPublished();

    /**
     * 获取文章浏览量top10
     * @return 文章浏览量top10
     */
    List<ArticleTitleViewCountDTO> getViewTop10();

    /**
     * 获取本月热门文章点赞榜（前 5 篇）
     * @return 本月点赞数最高的已发布文章列表
     */
    List<HotArticleVO> getMonthHotArticlesByLike();

    /**
     * 获取本月热门文章浏览榜（前 5 篇）
     * @return 本月浏览量最高的已发布文章列表
     */
    List<HotArticleVO> getMonthHotArticlesByView();

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
