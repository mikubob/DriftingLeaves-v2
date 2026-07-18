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
 * 访客
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("visitors")
@EqualsAndHashCode(callSuper = true)
public class Visitors extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 访客指纹，用于唯一标识访客
    private String fingerprint;

    // 会话 ID(当前浏览器会话)
    private String sessionId;

    // IP 地址
    private String ip;

    // 用户代理
    private String userAgent;

    // 国家
    private String country;

    // 省份
    private String province;

    // 城市
    private String city;

    // 首次访问时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime firstVisitTime;

    // 最后访问时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastVisitTime;

    // 访问次数
    private Long totalViews;

    // 是否被封禁，0-否，1-是
    private Integer isBlocked;

    // 封禁结束时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
