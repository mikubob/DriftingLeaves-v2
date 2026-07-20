package com.xuan.controller.blog;


import com.xuan.annotation.RateLimit;
import com.xuan.auth.security.SecurityUser;
import com.xuan.result.Result;
import com.xuan.service.IArticleLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 博客端文章点赞接口
 * <p>
 * 写接口（POST/DELETE）需 GUEST 角色（由 ResourceServerConfig 控制），
 * userId 由 {@link SecurityUser} 中获取，不再由前端传入。
 * GET 接口公开访问，未登录时 userId 为 null。
 * </p>
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
    public Result<String> like(@PathVariable Long articleId,
                               @AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser.getUserId();
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
    public Result<String> unlike(@PathVariable Long articleId,
                                 @AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser.getUserId();
        log.info("用户取消点赞: articleId={}, userId={}", articleId, userId);
        articleLikeService.unlikeArticle(articleId, userId);
        return Result.success();
    }

    /**
     * 检查是否已点赞
     * <p>GET 接口公开访问，未登录时返回 false。</p>
     */
    @GetMapping("/{articleId}")
    public Result<Boolean> hasLiked(@PathVariable Long articleId,
                                    @AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser != null ? securityUser.getUserId() : null;
        boolean liked = userId != null && articleLikeService.hasLiked(articleId, userId);
        log.info("检查是否已点赞: articleId={}, userId={}, liked={}", articleId, userId, liked);
        return Result.success(liked);
    }
}
