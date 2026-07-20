package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.auth.security.SecurityUser;
import com.xuan.dto.RssSubscriptionDTO;
import com.xuan.result.Result;
import com.xuan.service.IRssSubscriptionService;
import com.xuan.vo.RssSubscriptionStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 博客端 RSS 订阅接口
 * <p>
 * 阶段四：写接口需 GUEST 角色（由 ResourceServerConfig 控制），
 * userId 由 {@link SecurityUser} 中获取，不再由前端传入。
 * unsubscribe 接口由 ResourceServerConfig 单独放行，支持邮件链接匿名退订。
 * </p>
 */
@Slf4j
@RestController("blogRssSubscriptionController")
@RequestMapping("/blog/rssSubscription")
@RequiredArgsConstructor
public class RssSubscriptionController {

    private final IRssSubscriptionService rssSubscriptionService;

    /**
     * 添加RSS订阅
     */
    @PostMapping
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
            timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result<String> addSubscription(@Valid @RequestBody RssSubscriptionDTO rssSubscriptionDTO,
                                          @AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser.getUserId();
        log.info("添加RSS订阅: userId={}, dto={}", userId, rssSubscriptionDTO);
        rssSubscriptionService.addSubscription(rssSubscriptionDTO, userId);
        return Result.success();
    }

    /**
     * 取消RSS订阅（访客端）
     * <p>通过邮件链接匿名访问，ResourceServerConfig 单独放行。</p>
     */
    @PutMapping("/unsubscribe")
    public Result<String> unsubscribe(@RequestParam String email) {
        log.info("取消RSS订阅: email={}", email);
        rssSubscriptionService.unsubscribeByEmail(email);
        return Result.success();
    }

    /**
     * 检查用户订阅状态（返回订阅详情）
     * <p>GET 接口公开访问，未登录时返回未订阅状态。</p>
     */
    @GetMapping("/check")
    public Result<RssSubscriptionStatusVO> checkSubscription(@AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser != null ? securityUser.getUserId() : null;
        log.info("检查订阅状态: userId={}", userId);
        RssSubscriptionStatusVO status = rssSubscriptionService.getSubscriptionStatus(userId);
        return Result.success(status);
    }
}
