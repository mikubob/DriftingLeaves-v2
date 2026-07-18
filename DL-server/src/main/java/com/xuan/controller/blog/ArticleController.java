package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.constant.RedisConstant;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IArticleService;
import com.xuan.vo.ArticleArchiveVO;
import com.xuan.vo.BlogArticleDetailVO;
import com.xuan.vo.BlogArticleVO;
import com.xuan.vo.HotArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端文章接口
 */
@RestController("blogArticleController")
@RequestMapping("/blog/article")
@Slf4j
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articleService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 获取已发布文章列表（分页）
     */
    @GetMapping("/page")
    public Result<PageResult<BlogArticleVO>> getPublishedPage(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int pageSize) {
        log.info("博客端获取已发布文章列表: page={}, pageSize={}", page, pageSize);
        PageResult<BlogArticleVO> pageResult = articleService.getPublishedPage(page, pageSize);
        pageResult.getRecords().forEach(this::mergeRedisCount);
        return Result.success(pageResult);
    }

    /**
     * 根据 slug 获取文章详情（浏览量 +1）
     */
    @GetMapping("/detail/{slug}")
    public Result<BlogArticleDetailVO> getBySlug(@PathVariable String slug) {
        log.info("博客端获取文章详情: slug={}", slug);
        BlogArticleDetailVO articleDetail = articleService.getBySlug(slug);
        articleService.incrementViewCount(articleDetail.getId());
        mergeRedisCount(articleDetail);
        return Result.success(articleDetail);
    }

    /**
     * 根据分类 ID 获取文章列表（分页）
     */
    @GetMapping("/category/{categoryId}")
    public Result<PageResult<BlogArticleVO>> getByCategory(@PathVariable Long categoryId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int pageSize) {
        log.info("博客端根据分类获取文章列表: categoryId={}, page={}, pageSize={}", categoryId, page, pageSize);
        PageResult<BlogArticleVO> pageResult = articleService.getPublishedByCategoryId(categoryId, page, pageSize);
        pageResult.getRecords().forEach(this::mergeRedisCount);
        return Result.success(pageResult);
    }

    /**
     * 获取文章归档
     */
    @GetMapping("/archive")
    public Result<List<ArticleArchiveVO>> getArchive() {
        log.info("博客端获取文章归档");
        return Result.success(articleService.getArchive());
    }

    /**
     * 获取本月热门文章点赞榜（前 5 篇）
     */
    @GetMapping("/hot/month/like")
    public Result<List<HotArticleVO>> getMonthHotArticlesByLike() {
        log.info("博客端获取本月热门文章点赞榜");
        return Result.success(articleService.getMonthHotArticlesByLike());
    }

    /**
     * 获取本月热门文章浏览榜（前 5 篇）
     */
    @GetMapping("/hot/month/view")
    public Result<List<HotArticleVO>> getMonthHotArticlesByView() {
        log.info("博客端获取本月热门文章浏览榜");
        return Result.success(articleService.getMonthHotArticlesByView());
    }

    /**
     * 获取全站热门文章点赞榜（前 5 篇）
     */
    @GetMapping("/hot/site/like")
    public Result<List<HotArticleVO>> getSiteHotArticlesByLike() {
        log.info("博客端获取全站热门文章点赞榜");
        return Result.success(articleService.getSiteHotArticlesByLike());
    }

    /**
     * 获取全站热门文章浏览榜（前 5 篇）
     */
    @GetMapping("/hot/site/view")
    public Result<List<HotArticleVO>> getSiteHotArticlesByView() {
        log.info("博客端获取全站热门文章浏览榜");
        return Result.success(articleService.getSiteHotArticlesByView());
    }

    /**
     * 文章搜索（仅已发布）
     */
    @GetMapping("/search")
    @RateLimit(type = RateLimit.Type.IP, tokens = 10, burstCapacity = 15,
            timeWindow = 60, message = "搜索过于频繁，请稍后再试")
    public Result<PageResult<BlogArticleVO>> search(@RequestParam String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        log.info("博客端文章搜索: keyword={}", keyword);
        PageResult<BlogArticleVO> pageResult = articleService.searchPublished(keyword, page, pageSize);
        pageResult.getRecords().forEach(this::mergeRedisCount);
        return Result.success(pageResult);
    }

    /**
     * 合并 Redis 中未落库的浏览量和点赞量增量
     */
    private void mergeRedisCount(BlogArticleVO article) {
        if (article == null || article.getId() == null) {
            return;
        }

        String articleIdStr = article.getId().toString();

        Object pendingView = stringRedisTemplate.opsForHash().get(RedisConstant.ARTICLE_VIEW_COUNT, articleIdStr);
        if (pendingView != null) {
            long pendingViewCount = Long.parseLong(pendingView.toString());
            article.setViewCount(safeLong(article.getViewCount()) + pendingViewCount);
        }

        Object pendingLike = stringRedisTemplate.opsForHash().get(RedisConstant.ARTICLE_LIKE_COUNT, articleIdStr);
        if (pendingLike != null) {
            long pendingLikeCount = Long.parseLong(pendingLike.toString());
            article.setLikeCount(safeLong(article.getLikeCount()) + pendingLikeCount);
        }
    }

    /**
     * 合并 Redis 中未落库的浏览量和点赞量增量（详情页）
     */
    private void mergeRedisCount(BlogArticleDetailVO article) {
        if (article == null || article.getId() == null) {
            return;
        }

        String articleIdStr = article.getId().toString();

        Object pendingView = stringRedisTemplate.opsForHash().get(RedisConstant.ARTICLE_VIEW_COUNT, articleIdStr);
        if (pendingView != null) {
            long pendingViewCount = Long.parseLong(pendingView.toString());
            article.setViewCount(safeLong(article.getViewCount()) + pendingViewCount);
        }

        Object pendingLike = stringRedisTemplate.opsForHash().get(RedisConstant.ARTICLE_LIKE_COUNT, articleIdStr);
        if (pendingLike != null) {
            long pendingLikeCount = Long.parseLong(pendingLike.toString());
            article.setLikeCount(safeLong(article.getLikeCount()) + pendingLikeCount);
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
