package com.xuan.service.impl;

import com.xuan.constant.MessageConstant;
import com.xuan.constant.RedisConstant;
import com.xuan.entity.Visitors;
import com.xuan.exception.BlockedException;
import com.xuan.service.BlockService;
import com.xuan.service.IVisitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BlockServiceImpl implements BlockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final IVisitorService visitorService;

    public BlockServiceImpl(RedisTemplate<String, Object> redisTemplate,
                            @Lazy IVisitorService visitorService) {
        this.redisTemplate = redisTemplate;
        this.visitorService = visitorService;
    }

    // 访问频率限制配置
    private static final int IP_RATE_LIMIT = 60; // 每分钟最多访问次数
    private static final int FINGERPRINT_RATE_LIMIT = 1000; // 每小时最多访问次数
    private static final int BLOCK_DURATION_DAYS = 1; // 封禁持续时间（天）


    /**
     * 检查缓存是否被封禁
     *
     * @param fingerprint 指纹
     */
    @Override
    public void checkIfBlocked(String fingerprint) {
        //1.先检查redis缓存
        String blockedKey = RedisConstant.BLOCKED_KEY + fingerprint;
        Boolean isBlocked = redisTemplate.hasKey(blockedKey);

        if (Boolean.TRUE.equals(isBlocked)) {
            throw new BlockedException(MessageConstant.VISITOR_BLOCKED);
        }

        //2.检查数据库
        Visitors visitor = visitorService.findVisitorByFingerprint(fingerprint);
        if (visitor != null && visitor.getIsBlocked() == 1) {
            if (visitor.getExpiresAt() == null
                    || visitor.getExpiresAt().isAfter(LocalDateTime.now())) {
                //封禁中，更新Redis缓存
                long expireSeconds = visitor.getExpiresAt() != null 
                        ? ChronoUnit.SECONDS.between(LocalDateTime.now(), visitor.getExpiresAt()) 
                        : TimeUnit.DAYS.toSeconds(BLOCK_DURATION_DAYS);
                redisTemplate.opsForValue().set(blockedKey, "1", expireSeconds, TimeUnit.SECONDS);
                log.info("更新Redis封禁缓存: fingerprint={}, expiresAt={}, expireSeconds={}", 
                        fingerprint, visitor.getExpiresAt(), expireSeconds);
                throw new BlockedException(MessageConstant.VISITOR_BLOCKED);
            } else {
                //封禁已经结束，解除封禁
                log.info("【访客追踪】封禁过期自动解封: id={}, fingerprint={}, expiresAt={}",
                        visitor.getId(), fingerprint, visitor.getExpiresAt());
                visitorService.updateById(Visitors.builder()
                        .id(visitor.getId())
                        .isBlocked(0)
                        .expiresAt(null)
                        .build());
                // 删除Redis缓存
                redisTemplate.delete(blockedKey);
                log.info("删除Redis封禁缓存: fingerprint={}", fingerprint);
            }
        }
    }

    /**
     * 检查访问频率
     * @param fingerprint 指纹
     * @param ip  IP
     */
    @Override
    public void checkRateLimit(String fingerprint, String ip) {
        // Ip级别限制：每分钟最多访问次数
        String ipKey = RedisConstant.RATE_LIMIT_KEY + "ip:" + ip;
        Long ipCount = redisTemplate.opsForValue().increment(ipKey, 1);
        if (ipCount == 1){
            redisTemplate.expire(ipKey, 1, TimeUnit.MINUTES);
        }
        if (ipCount > IP_RATE_LIMIT){
            //自动封禁
            log.warn("IP访问频率过高，触发自动封禁: ip={}, count={}, limit={}", ip, ipCount, IP_RATE_LIMIT);
            blockVisitor(fingerprint);
            throw new BlockedException(MessageConstant.VISITOR_BLOCKED);
        }

        //指纹级别限制：每小时最多访问次数
        String fingerprintKey = RedisConstant.RATE_LIMIT_KEY + "fingerprint:" + fingerprint;
        Long fingerprintCount = redisTemplate.opsForValue().increment(fingerprintKey, 1);
        if (fingerprintCount == 1){
            redisTemplate.expire(fingerprintKey, 1, TimeUnit.HOURS);
        }
        if (fingerprintCount > FINGERPRINT_RATE_LIMIT){
            //自动封禁
            log.warn("指纹访问频率过高，触发自动封禁: fingerprint={}, ip={}, count={}, limit={}", fingerprint, ip, fingerprintCount, FINGERPRINT_RATE_LIMIT);
            blockVisitor(fingerprint);
            throw new BlockedException(MessageConstant.VISITOR_BLOCKED);
        }
    }

    /**
     * 封禁访客
     * @param fingerprint
     */
    @Transactional
    protected void blockVisitor(String fingerprint) {
        Visitors visitor = visitorService.findVisitorByFingerprint(fingerprint);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(BLOCK_DURATION_DAYS);
        
        if (visitor != null) {
            visitor.setIsBlocked(1);
            visitor.setExpiresAt(expiresAt);
            visitorService.updateById(visitor);
            log.warn("更新访客封禁状态: id={}, fingerprint={}, expiresAt={}", visitor.getId(), fingerprint, expiresAt);
        } else {
            // 创建新的访客记录并设置为封禁状态
            visitor = Visitors.builder()
                    .fingerprint(fingerprint)
                    .isBlocked(1)
                    .expiresAt(expiresAt)
                    .build();
            visitorService.save(visitor);
            log.warn("创建新访客并封禁: fingerprint={}, expiresAt={}", fingerprint, expiresAt);
        }

        // 更新Redis缓存，根据 expiresAt 计算过期时间
        String blockedKey = RedisConstant.BLOCKED_KEY + fingerprint;
        long expireSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), expiresAt);
        redisTemplate.opsForValue().set(blockedKey, "1", expireSeconds, TimeUnit.SECONDS);
        log.warn("更新Redis封禁缓存: fingerprint={}, expiresAt={}, expireSeconds={}", fingerprint, expiresAt, expireSeconds);
    }
}
