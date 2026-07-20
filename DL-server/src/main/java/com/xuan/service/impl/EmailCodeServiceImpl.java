package com.xuan.service.impl;

import com.xuan.service.EmailCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.xuan.constant.RedisConstant.KEY_EMAIL_ATTEMPT_COUNT_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_EMAIL_LOCK_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_EMAIL_RATE_LIMIT_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_EMAIL_VERIFY_CODE_PREFIX;

/**
 * 邮箱验证码服务实现（按 email 维度隔离）
 * <p>
 * 与 {@link VerifyCodeServiceImpl}（管理端按 userId 维度）实现策略一致，
 * 仅 Redis Key 前缀不同，避免与 admin 端验证码冲突。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 时间常量（与管理端一致）
    private static final int RATE_LIMIT_SECONDS = 60; // 发送频率限制 60s
    private static final int CODE_TTL_MINUTES = 5;    // 验证码有效期 5 分钟
    private static final int MAX_ATTEMPTS = 5;        // 最大尝试次数
    private static final int LOCK_MINUTES = 30;       // 锁定 30 分钟

    @Override
    public String generateCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1_000_000));
    }

    @Override
    public void saveCode(String email, String code) {
        redisTemplate.opsForValue().set(key(KEY_EMAIL_VERIFY_CODE_PREFIX, email), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(key(KEY_EMAIL_RATE_LIMIT_PREFIX, email), "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        redisTemplate.delete(key(KEY_EMAIL_ATTEMPT_COUNT_PREFIX, email));
        redisTemplate.delete(key(KEY_EMAIL_LOCK_PREFIX, email));
    }

    @Override
    public boolean canSendCode(String email) {
        return redisTemplate.opsForValue().get(key(KEY_EMAIL_RATE_LIMIT_PREFIX, email)) == null;
    }

    @Override
    public Long getRemainingCooldown(String email) {
        Long ttl = redisTemplate.getExpire(key(KEY_EMAIL_RATE_LIMIT_PREFIX, email), TimeUnit.SECONDS);
        return ttl != null ? Math.max(ttl, 0) : 0;
    }

    @Override
    public boolean isLocked(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(KEY_EMAIL_LOCK_PREFIX, email)));
    }

    @Override
    public Long getLockRemainingMinutes(String email) {
        Long ttl = redisTemplate.getExpire(key(KEY_EMAIL_LOCK_PREFIX, email), TimeUnit.MINUTES);
        return ttl != null ? Math.max(ttl, 0) : 0;
    }

    @Override
    public boolean verifyCode(String email, String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        if (isLocked(email)) {
            return false;
        }

        String savedCode = (String) redisTemplate.opsForValue().get(key(KEY_EMAIL_VERIFY_CODE_PREFIX, email));
        if (savedCode == null) {
            recordFailedAttempt(email);
            return false;
        }

        boolean success = savedCode.equals(code.trim());
        if (success) {
            clearAll(email);
            return true;
        } else {
            recordFailedAttempt(email);
            return false;
        }
    }

    @Override
    public Long getRemainingAttempts(String email) {
        if (isLocked(email)) {
            return 0L;
        }
        return Math.max(MAX_ATTEMPTS - getAttemptCount(email), 0);
    }

    // ===== 内部辅助方法 =====

    private void recordFailedAttempt(String email) {
        String attemptKey = key(KEY_EMAIL_ATTEMPT_COUNT_PREFIX, email);

        Long attemptCount = redisTemplate.opsForValue().increment(attemptKey, 1L);
        if (attemptCount == null) {
            attemptCount = 1L;
        }

        // 第一次失败时设置过期时间，与验证码同步失效
        if (attemptCount == 1) {
            long codeTtl = redisTemplate.getExpire(key(KEY_EMAIL_VERIFY_CODE_PREFIX, email), TimeUnit.SECONDS);
            if (codeTtl > 0) {
                redisTemplate.expire(attemptKey, codeTtl, TimeUnit.SECONDS);
            }
        }

        // 达到最大尝试次数，锁定 30 分钟
        if (attemptCount >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(key(KEY_EMAIL_LOCK_PREFIX, email), "1", LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    private void clearAll(String email) {
        redisTemplate.delete(key(KEY_EMAIL_VERIFY_CODE_PREFIX, email));
        redisTemplate.delete(key(KEY_EMAIL_RATE_LIMIT_PREFIX, email));
        redisTemplate.delete(key(KEY_EMAIL_ATTEMPT_COUNT_PREFIX, email));
        redisTemplate.delete(key(KEY_EMAIL_LOCK_PREFIX, email));
    }

    private Long getAttemptCount(String email) {
        try {
            Object value = redisTemplate.opsForValue().get(key(KEY_EMAIL_ATTEMPT_COUNT_PREFIX, email));
            if (value == null) {
                return 0L;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String key(String prefix, String email) {
        return prefix + email;
    }
}
