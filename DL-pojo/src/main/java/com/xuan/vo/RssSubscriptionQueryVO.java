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
 * RSS订阅分页查询返回VO
 * 基于前端 Rss/index.vue 实际使用字段设计
 * 
 * 注意：email 字段保留，因为前端删除确认弹窗需要显示
 * 移除敏感字段：visitorId
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RssSubscriptionQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 订阅ID
    private Long id;

    // 用户名
    private String username;

    // 订阅邮箱
    private String email;

    // 激活状态
    private Integer isActive;

    // 订阅时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime subscribeTime;

    // 取消订阅时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unSubscribeTime;
}
