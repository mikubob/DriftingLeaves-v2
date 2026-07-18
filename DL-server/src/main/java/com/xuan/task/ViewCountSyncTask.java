package com.xuan.task;

import com.xuan.constant.RedisConstant;
import com.xuan.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 文章浏览量定时同步任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountSyncTask {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ArticleMapper articleMapper;

    /**
     * 每5分钟将 Redis 中的浏览量增量同步到 MySQL
     * 使用 Redis 分布式锁防止多实例并发执行
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void syncViewCountToMySQL() {
        //1.尝试获取分布式锁，锁有效期4分钟
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(RedisConstant.LOCK_VIEW_COUNT_SYNC, "1", Duration.ofMinutes(4));
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("浏览量同步任务未获取到锁，跳过本次执行");
            return;
        }

        try {
            //2.获取 Redis 中所有浏览量增量
            Map<Object, Object> viewCounts = redisTemplate.opsForHash().entries(RedisConstant.ARTICLE_VIEW_COUNT);
            if (viewCounts == null || viewCounts.isEmpty()) {
                return;
            }

            //3.遍历同步每篇文章的浏览量
            int syncCount = 0;
            for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
                try {
                    Long articleId = Long.parseLong(entry.getKey().toString());
                    int increment = ((Number) entry.getValue()).intValue();

                    //3.1增量无效则删除并跳过
                    if (increment <= 0) {
                        redisTemplate.opsForHash().delete(RedisConstant.ARTICLE_VIEW_COUNT, entry.getKey());
                        continue;
                    }

                    //3.2更新 MySQL 浏览量
                    articleMapper.addViewCount(articleId, increment);

                    //3.3扣减 Redis 增量，若归零则删除 key
                    Long remaining = redisTemplate.opsForHash().increment(RedisConstant.ARTICLE_VIEW_COUNT, entry.getKey(), -increment);
                    if (remaining != null && remaining <= 0) {
                        redisTemplate.opsForHash().delete(RedisConstant.ARTICLE_VIEW_COUNT, entry.getKey());
                    }
                    syncCount++;
                } catch (Exception e) {
                    log.error("同步文章 {} 浏览量异常: {}", entry.getKey(), e.getMessage());
                }
            }

            //4.记录同步日志
            if (syncCount > 0) {
                log.info("浏览量同步完成，共同步 {} 篇文章", syncCount);
            }
        } catch (Exception e) {
            log.error("浏览量同步异常: {}", e.getMessage());
        } finally {
            //5.释放分布式锁
            redisTemplate.delete(RedisConstant.LOCK_VIEW_COUNT_SYNC);
        }
    }
}
