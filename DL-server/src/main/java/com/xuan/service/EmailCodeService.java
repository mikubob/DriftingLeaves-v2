package com.xuan.service;

/**
 * 邮箱验证码服务（博客端使用，按 email 维度隔离）
 * <p>
 * 与管理端 {@link VerifyCodeService}（按 userId 维度）正交：
 * 博客端用户注册时还没有 userId，验证码必须按 email 隔离存储。
 * </p>
 */
public interface EmailCodeService {

    /**
     * 生成 6 位数字验证码
     */
    String generateCode();

    /**
     * 保存验证码并设置发送频率限制
     *
     * @param email 邮箱
     * @param code  验证码
     */
    void saveCode(String email, String code);

    /**
     * 该邮箱是否可以发送验证码（频率限制）
     *
     * @param email 邮箱
     */
    boolean canSendCode(String email);

    /**
     * 获取剩余验证码冷却时间(秒)
     *
     * @param email 邮箱
     */
    Long getRemainingCooldown(String email);

    /**
     * 该邮箱是否被锁定（验证码尝试次数过多）
     *
     * @param email 邮箱
     */
    boolean isLocked(String email);

    /**
     * 获取锁定剩余时间(分钟)
     *
     * @param email 邮箱
     */
    Long getLockRemainingMinutes(String email);

    /**
     * 校验验证码
     *
     * @param email 邮箱
     * @param code  验证码
     * @return 是否校验通过
     */
    boolean verifyCode(String email, String code);
}
