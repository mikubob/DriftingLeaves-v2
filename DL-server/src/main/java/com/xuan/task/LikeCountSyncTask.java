package com.xuan.task;

import com.xuan.constant.RedisConstant;
import com.xuan.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 文章点赞数定时同步任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountSyncTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;

    /**
     * 每5分钟将 Redis 中的点赞数增量同步到 MySQL
     * 使用 Redis 分布式锁防止多实例并发执行
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void syncLikeCountToMySQL() {
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisConstant.LOCK_LIKE_COUNT_SYNC, "1", Duration.ofMinutes(4));
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("点赞数同步任务未获取到锁，跳过本次执行");
            return;
        }

        try {
            Map<Object, Object> likeCounts = stringRedisTemplate.opsForHash().entries(RedisConstant.ARTICLE_LIKE_COUNT);
            if (likeCounts == null || likeCounts.isEmpty()) {
                return;
            }

            int syncCount = 0;
            for (Map.Entry<Object, Object> entry : likeCounts.entrySet()) {
                try {
                    Long articleId = Long.parseLong(entry.getKey().toString());
                    int increment = Integer.parseInt(entry.getValue().toString());

                    if (increment == 0) {
                        stringRedisTemplate.opsForHash().delete(RedisConstant.ARTICLE_LIKE_COUNT, entry.getKey());
                        continue;
                    }

                    articleMapper.addLikeCount(articleId, increment);

                    stringRedisTemplate.opsForHash().delete(RedisConstant.ARTICLE_LIKE_COUNT, entry.getKey());
                    syncCount++;
                } catch (Exception e) {
                    log.error("同步文章 {} 点赞数异常: {}", entry.getKey(), e.getMessage());
                }
            }

            if (syncCount > 0) {
                log.info("点赞数同步完成，共同步 {} 篇文章", syncCount);
            }
        } catch (Exception e) {
            log.error("点赞数同步异常: {}", e.getMessage());
        } finally {
            stringRedisTemplate.delete(RedisConstant.LOCK_LIKE_COUNT_SYNC);
        }
    }
}
