package com.xuan.service.impl;

import com.xuan.service.LoginLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.xuan.constant.RedisConstant.KEY_LOGIN_ATTEMPT_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_LOGIN_LOCK_PREFIX;

/**
 * 登录锁定服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockServiceImpl implements LoginLockService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 时间常量
    private static final int MAX_ATTEMPTS = 5;        // 最大失败次数
    private static final int LOCK_MINUTES = 30;       // 锁定 30 分钟
    private static final int ATTEMPT_TTL_MINUTES = 30; // 失败计数 30 分钟后过期

    @Override
    public boolean isLocked(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(KEY_LOGIN_LOCK_PREFIX, ip)));
    }

    @Override
    public Long getLockRemainingMinutes(String ip) {
        Long ttl = redisTemplate.getExpire(key(KEY_LOGIN_LOCK_PREFIX, ip), TimeUnit.MINUTES);
        return ttl != null ? Math.max(ttl, 0) : 0;
    }

    @Override
    public void recordFailedLogin(String ip) {
        String attemptKey = key(KEY_LOGIN_ATTEMPT_PREFIX, ip);

        Long attemptCount = redisTemplate.opsForValue().increment(attemptKey, 1L);
        if (attemptCount == null) {
            attemptCount = 1L;
        }

        // 第一次失败时设置过期时间
        if (attemptCount == 1) {
            redisTemplate.expire(attemptKey, ATTEMPT_TTL_MINUTES, TimeUnit.MINUTES);
        }

        // 达到最大尝试次数，锁定该 IP
        if (attemptCount >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(key(KEY_LOGIN_LOCK_PREFIX, ip), "1", LOCK_MINUTES, TimeUnit.MINUTES);
            log.warn("IP 登录失败次数过多已被锁定：ip={}", ip);
        }
    }

    @Override
    public void clear(String ip) {
        redisTemplate.delete(key(KEY_LOGIN_ATTEMPT_PREFIX, ip));
        redisTemplate.delete(key(KEY_LOGIN_LOCK_PREFIX, ip));
    }

    private String key(String prefix, String ip) {
        return prefix + ip;
    }
}
