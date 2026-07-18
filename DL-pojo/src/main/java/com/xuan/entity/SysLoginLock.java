package com.xuan.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xuan.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 用户登录锁定记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_login_lock")
@EqualsAndHashCode(callSuper = true)
public class SysLoginLock extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID（按IP锁定时可为空）
    private Long userId;

    // IP地址
    private String ipAddress;

    // 连续失败次数
    private Integer failedCount;

    // 锁定截止时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lockUntil;

    // 最后一次失败时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastFailTime;
}
