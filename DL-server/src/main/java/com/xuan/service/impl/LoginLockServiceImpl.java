package com.xuan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xuan.entity.SysLoginLock;
import com.xuan.mapper.SysLoginLockMapper;
import com.xuan.service.LoginLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 登录锁定服务实现
 * <p>
 * 基于 sys_login_lock 表持久化登录失败记录，达到阈值后锁定该 IP。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockServiceImpl implements LoginLockService {

    private final SysLoginLockMapper sysLoginLockMapper;

    // 时间常量
    private static final int MAX_ATTEMPTS = 5;         // 最大失败次数
    private static final int LOCK_MINUTES = 30;        // 锁定 30 分钟
    private static final int ATTEMPT_TTL_MINUTES = 30; // 失败计数 30 分钟后过期

    @Override
    public boolean isLocked(String ip) {
        SysLoginLock lock = getByIp(ip);
        return lock != null && lock.getLockUntil() != null && lock.getLockUntil().isAfter(LocalDateTime.now());
    }

    @Override
    public Long getLockRemainingMinutes(String ip) {
        SysLoginLock lock = getByIp(ip);
        if (lock == null || lock.getLockUntil() == null) {
            return 0L;
        }
        long remaining = ChronoUnit.MINUTES.between(LocalDateTime.now(), lock.getLockUntil());
        return Math.max(remaining, 0L);
    }

    @Override
    public void recordFailedLogin(String ip) {
        LocalDateTime now = LocalDateTime.now();
        SysLoginLock lock = getByIp(ip);

        if (lock == null) {
            // 首次失败，插入记录
            SysLoginLock newLock = SysLoginLock.builder()
                    .ipAddress(ip)
                    .failedCount(1)
                    .lastFailTime(now)
                    .build();
            sysLoginLockMapper.insert(newLock);
            return;
        }

        // 如果上次失败时间距离现在超过过期时间，重置计数
        Integer failedCount = lock.getFailedCount() == null ? 0 : lock.getFailedCount();
        if (lock.getLastFailTime() != null
                && ChronoUnit.MINUTES.between(lock.getLastFailTime(), now) > ATTEMPT_TTL_MINUTES) {
            failedCount = 0;
        }

        failedCount++;
        LocalDateTime lockUntil = lock.getLockUntil();
        if (failedCount >= MAX_ATTEMPTS) {
            lockUntil = now.plusMinutes(LOCK_MINUTES);
            log.warn("IP 登录失败次数过多已被锁定：ip={}", ip);
        }

        LambdaUpdateWrapper<SysLoginLock> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SysLoginLock::getIpAddress, ip)
                .set(SysLoginLock::getFailedCount, failedCount)
                .set(SysLoginLock::getLastFailTime, now)
                .set(lockUntil != null, SysLoginLock::getLockUntil, lockUntil);
        sysLoginLockMapper.update(updateWrapper);
    }

    @Override
    public void clear(String ip) {
        LambdaQueryWrapper<SysLoginLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginLock::getIpAddress, ip);
        sysLoginLockMapper.delete(wrapper);
    }

    /**
     * 根据 IP 查询登录锁定记录
     *
     * @param ip 客户端 IP
     * @return 锁定记录，不存在返回 null
     */
    private SysLoginLock getByIp(String ip) {
        LambdaQueryWrapper<SysLoginLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginLock::getIpAddress, ip);
        return sysLoginLockMapper.selectOne(wrapper);
    }
}
