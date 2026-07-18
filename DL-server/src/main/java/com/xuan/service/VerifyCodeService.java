package com.xuan.service;

/**
 * 验证码服务
 */
public interface VerifyCodeService {

    /**
     * 生成验证码
     */
    String generateCode();

    /**
     * 保存验证码并设置发送频率
     *
     * @param userId 用户 ID
     * @param code   验证码
     */
    void saveCode(Long userId, String code);

    /**
     * 邮箱是否可以发送验证码（频率限制）
     *
     * @param userId 用户 ID
     */
    boolean canSendCode(Long userId);

    /**
     * 获取剩余验证码冷却时间(秒)
     *
     * @param userId 用户 ID
     */
    Long getRemainingCooldown(Long userId);

    /**
     * 是否被锁定
     *
     * @param userId 用户 ID
     */
    boolean isLocked(Long userId);

    /**
     * 获取锁定剩余时间（分钟）
     *
     * @param userId 用户 ID
     */
    Long getLockRemainingMinutes(Long userId);

    /**
     * 是否允许尝试验证
     *
     * @param userId 用户 ID
     */
    boolean canAttempt(Long userId);

    /**
     * 验证验证码
     *
     * @param userId 用户 ID
     * @param code   验证码
     */
    boolean verifyCode(Long userId, String code);

    /**
     * 获取剩余尝试次数
     *
     * @param userId 用户 ID
     */
    Long getRemainingAttempts(Long userId);
}
