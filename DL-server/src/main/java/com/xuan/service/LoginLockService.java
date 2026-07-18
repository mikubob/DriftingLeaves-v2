package com.xuan.service;

/**
 * 登录锁定服务
 * <p>
 * 基于 IP 维度统计登录失败次数，达到阈值后锁定该 IP。
 */
public interface LoginLockService {

    /**
     * 判断该 IP 是否已被锁定
     *
     * @param ip 客户端 IP
     * @return true 表示已锁定
     */
    boolean isLocked(String ip);

    /**
     * 获取该 IP 剩余锁定时间（分钟）
     *
     * @param ip 客户端 IP
     * @return 剩余分钟数
     */
    Long getLockRemainingMinutes(String ip);

    /**
     * 记录一次登录失败
     *
     * @param ip 客户端 IP
     */
    void recordFailedLogin(String ip);

    /**
     * 登录成功后清空该 IP 的失败记录
     *
     * @param ip 客户端 IP
     */
    void clear(String ip);
}
