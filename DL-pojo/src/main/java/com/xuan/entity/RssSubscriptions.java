package com.xuan.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rss 订阅记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rss_subscriptions")
public class RssSubscriptions implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 用户名（从 sys_user 同步）
    private String username;

    // 邮箱（从 sys_user 同步）
    private String email;

    // 是否激活，0-否，1-是
    private Integer isActive;

    // 订阅时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime subscribeTime;

    // 取消订阅时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unSubscribeTime;
}
