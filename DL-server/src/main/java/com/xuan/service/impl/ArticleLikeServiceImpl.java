package com.xuan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.RedisConstant;
import com.xuan.entity.ArticleLikes;
import com.xuan.mapper.ArticleLikeMapper;
import com.xuan.service.IArticleLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 文章点赞服务实现类
 * 优化：使用 Redis ZSet 存储点赞用户信息，Redis Hash 存储点赞数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleLikeServiceImpl extends ServiceImpl<ArticleLikeMapper, ArticleLikes> implements IArticleLikeService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 点赞文章
     *
     * @param articleId 文章 ID
     * @param userId 用户 ID
     */
    @Override
    public void likeArticle(Long articleId, Long userId) {
        String key = RedisConstant.ARTICLE_LIKE_USER_SET + articleId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score != null) {
            return;
        }

        stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());

        stringRedisTemplate.opsForHash().increment(RedisConstant.ARTICLE_LIKE_COUNT, articleId.toString(), 1);
    }

    /**
     * 取消点赞
     *
     * @param articleId 文章 ID
     * @param userId 用户 ID
     */
    @Override
    public void unlikeArticle(Long articleId, Long userId) {
        String key = RedisConstant.ARTICLE_LIKE_USER_SET + articleId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            return;
        }

        stringRedisTemplate.opsForZSet().remove(key, userId.toString());

        stringRedisTemplate.opsForHash().increment(RedisConstant.ARTICLE_LIKE_COUNT, articleId.toString(), -1);
    }

    /**
     * 检查是否已点赞
     *
     * @param articleId 文章 ID
     * @param userId 用户 ID
     * @return 是否已点赞
     */
    @Override
    public boolean hasLiked(Long articleId, Long userId) {
        String key = RedisConstant.ARTICLE_LIKE_USER_SET + articleId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        return score != null;
    }
}
