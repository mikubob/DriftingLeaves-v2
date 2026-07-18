package com.xuan.controller.blog;


import com.xuan.annotation.RateLimit;
import com.xuan.result.Result;
import com.xuan.service.IArticleLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 博客端文章点赞接口
 */
@RestController("blogArticleLikeController")
@RequestMapping("/blog/articleLike")
@Slf4j
@RequiredArgsConstructor
public class ArticleLikeController {

    private final IArticleLikeService articleLikeService;

    /**
     * 点赞文章
     */
    @PostMapping("/{articleId}")
    @RateLimit(type = RateLimit.Type.IP, tokens = 10, burstCapacity = 15,
              timeWindow = 60, message = "点赞操作过于频繁，请稍后再试")
    public Result<String> like(@PathVariable Long articleId, @RequestParam Long userId) {
        log.info("用户点赞文章: articleId={}, userId={}", articleId, userId);
        articleLikeService.likeArticle(articleId, userId);
        return Result.success();
    }

    /**
     * 取消点赞
     */
    @DeleteMapping("/{articleId}")
    @RateLimit(type = RateLimit.Type.IP, tokens = 10, burstCapacity = 15,
              timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result<String> unlike(@PathVariable Long articleId, @RequestParam Long userId) {
        log.info("用户取消点赞: articleId={}, userId={}", articleId, userId);
        articleLikeService.unlikeArticle(articleId, userId);
        return Result.success();
    }

    /**
     * 检查是否已点赞
     */
    @GetMapping("/{articleId}")
    public Result<Boolean> hasLiked(@PathVariable Long articleId, @RequestParam Long userId) {
        log.info("检查是否已点赞: articleId={}, userId={}", articleId, userId);
        boolean liked = articleLikeService.hasLiked(articleId, userId);
        return Result.success(liked);
    }
}
