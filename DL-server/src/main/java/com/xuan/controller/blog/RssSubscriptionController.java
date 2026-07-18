package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.RssSubscriptionDTO;
import com.xuan.result.Result;
import com.xuan.service.IRssSubscriptionService;
import com.xuan.vo.RssSubscriptionStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 博客端 RSS 订阅接口
 */
@Slf4j
@RestController("blogRssSubscriptionController")
@RequestMapping("/blog/rssSubscription")
@RequiredArgsConstructor
public class RssSubscriptionController {

    private final IRssSubscriptionService rssSubscriptionService;

    /**
     * 添加RSS订阅
     * @param rssSubscriptionDTO
     * @return
     */
    @PostMapping
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
            timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result addSubscription(@Valid @RequestBody RssSubscriptionDTO rssSubscriptionDTO) {
        log.info("添加RSS订阅,{}", rssSubscriptionDTO);
        rssSubscriptionService.addSubscription(rssSubscriptionDTO);
        return Result.success();
    }

    /**
     * 取消RSS订阅（访客端）
     * @param email
     * @return
     */
    @PutMapping("/unsubscribe")
    public Result unsubscribe(@RequestParam String email) {
        log.info("取消RSS订阅,{}", email);
        rssSubscriptionService.unsubscribeByEmail(email);
        return Result.success();
    }

    /**
     * 检查用户订阅状态（返回订阅详情）
     */
    @GetMapping("/check")
    public Result<RssSubscriptionStatusVO> checkSubscription(@RequestParam Long userId) {
        log.info("检查订阅状态: userId={}", userId);
        RssSubscriptionStatusVO status = rssSubscriptionService.getSubscriptionStatus(userId);
        return Result.success(status);
    }
}
