package com.xuan.service.impl;

import com.xuan.service.VerifyCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.xuan.constant.RedisConstant.KEY_ATTEMPT_COUNT_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_LOCK_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_RATE_LIMIT_PREFIX;
import static com.xuan.constant.RedisConstant.KEY_VERIFY_CODE_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyCodeServiceImpl implements VerifyCodeService {

    private final RedisTemplate<String, Object> redisTemplate;


    // 时间常量
    private static final int RATE_LIMIT_SECONDS = 60; // 发送频率限制60秒
    private static final int CODE_TTL_MINUTES = 5;    // 验证码有效期5分钟
    private static final int MAX_ATTEMPTS = 5;        // 最大尝试次数
    private static final int LOCK_MINUTES = 30;       // 锁定30分钟


    /**
     * 生成验证码
     *
     * @return 验证码
     */
    @Override
    public String generateCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1_000_000));// 生成6位数字验证码
    }

    /**
     * 保存验证码
     *
     * @param userId 用户 ID
     * @param code   验证码
     */
    @Override
    public void saveCode(Long userId, String code) {
        // 保存验证码
        redisTemplate.opsForValue().set(key(KEY_VERIFY_CODE_PREFIX, userId), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        // 设置发送频率限制
        redisTemplate.opsForValue().set(key(KEY_RATE_LIMIT_PREFIX, userId), "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        // 重置尝试计数和锁定状态
        redisTemplate.delete(key(KEY_ATTEMPT_COUNT_PREFIX, userId));
        redisTemplate.delete(key(KEY_LOCK_PREFIX, userId));
    }

    /**
     * 判断是否可以发送验证码
     *
     * @param userId 用户 ID
     * @return 是否可以发送验证码
     */
    @Override
    public boolean canSendCode(Long userId) {
        return redisTemplate.opsForValue().get(key(KEY_RATE_LIMIT_PREFIX, userId)) == null;
    }

    /**
     * 获取剩余冷却时间(秒)
     *
     * @param userId 用户 ID
     * @return 剩余冷却时间
     */
    @Override
    public Long getRemainingCooldown(Long userId) {
        Long ttl = redisTemplate.getExpire(key(KEY_RATE_LIMIT_PREFIX, userId), TimeUnit.SECONDS);
        return ttl != null ? Math.max(ttl, 0) : 0;
    }

    /**
     * 判断是否被锁定
     *
     * @param userId 用户 ID
     * @return 是否被锁定
     */
    @Override
    public boolean isLocked(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(KEY_LOCK_PREFIX, userId)));
    }

    /**
     * 获取锁定剩余时间(分钟)
     *
     * @param userId 用户 ID
     * @return 锁定剩余时间
     */
    @Override
    public Long getLockRemainingMinutes(Long userId) {
        Long ttl = redisTemplate.getExpire(key(KEY_LOCK_PREFIX, userId), TimeUnit.MINUTES);
        return ttl != null ? Math.max(ttl, 0) : 0;
    }

    /**
     * 判断是否允许尝试
     *
     * @param userId 用户 ID
     * @return 是否允许尝试
     */
    @Override
    public boolean canAttempt(Long userId) {
        // 检查是否被锁定
        return !isLocked(userId);
    }

    /**
     * 验证验证码
     *
     * @param userId 用户 ID
     * @param code   验证码
     * @return 验证结果
     */
    @Override
    public boolean verifyCode(Long userId, String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        // 检查是否被锁定
        if (isLocked(userId)) {
            return false;
        }

        // 检查验证码是否存在
        String savedCode = (String) redisTemplate.opsForValue().get(key(KEY_VERIFY_CODE_PREFIX, userId));
        if (savedCode == null) {
            // 验证失败，记录尝试
            recordFailedAttempt(userId);
            return false;
        }

        // 验证
        boolean success = savedCode.equals(code.trim());

        if (success) {
            // 验证成功
            clearAll(userId); // 清空所有验证相关数据
            return true;
        } else {
            // 验证失败，记录尝试
            recordFailedAttempt(userId);
            return false;
        }
    }

    /**
     * 获取剩余尝试次数
     *
     * @param userId 用户 ID
     * @return 剩余尝试次数
     */
    @Override
    public Long getRemainingAttempts(Long userId) {
        if (isLocked(userId)) {
            return 0L;
        }
        Long attemptCount = getAttemptCount(userId);
        return Math.max(MAX_ATTEMPTS - attemptCount, 0);
    }

    // 记录失败尝试
    private void recordFailedAttempt(Long userId) {
        String attemptKey = key(KEY_ATTEMPT_COUNT_PREFIX, userId);

        // 增加失败计数
        Long attemptCount = redisTemplate.opsForValue().increment(attemptKey, 1L);
        if (attemptCount == null) {
            attemptCount = 1L;
        }

        // 如果是第一次失败，设置过期时间
        if (attemptCount == 1) {
            long codeTtl = redisTemplate.getExpire(key(KEY_VERIFY_CODE_PREFIX, userId), TimeUnit.SECONDS);
            if (codeTtl > 0) {
                redisTemplate.expire(attemptKey, codeTtl, TimeUnit.SECONDS);
            }
        }

        // 达到最大尝试次数，锁定
        if (attemptCount >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(key(KEY_LOCK_PREFIX, userId), "1", LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    // 重置状态
    public void clearAll(Long userId) {
        redisTemplate.delete(key(KEY_VERIFY_CODE_PREFIX, userId));
        redisTemplate.delete(key(KEY_RATE_LIMIT_PREFIX, userId));
        redisTemplate.delete(key(KEY_ATTEMPT_COUNT_PREFIX, userId));
        redisTemplate.delete(key(KEY_LOCK_PREFIX, userId));
    }

    // 获取当前尝试次数
    public Long getAttemptCount(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(key(KEY_ATTEMPT_COUNT_PREFIX, userId));
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

    // 拼接用户隔离的 Redis Key
    private String key(String prefix, Long userId) {
        return prefix + userId;
    }
}
