package com.xuan.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访客分页查询返回VO
 * 基于前端 Visitor/index.vue 实际使用字段设计
 * 
 * 注意：ip 字段保留，因为前端封禁确认弹窗需要显示
 * 移除敏感字段：fingerprint, sessionId, userAgent, longitude, latitude
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitorQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 访客ID
    private Long id;

    // IP地址
    private String ip;

    // 国家
    private String country;

    // 省份
    private String province;

    // 城市
    private String city;

    // 总浏览次数
    private Long totalViews;

    // 是否封禁
    private Integer isBlocked;

    // 封禁到期时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    // 首次访问时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime firstVisitTime;

    // 最近访问时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastVisitTime;
}
