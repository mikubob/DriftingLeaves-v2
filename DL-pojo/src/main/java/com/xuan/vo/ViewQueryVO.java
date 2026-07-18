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
 * 浏览记录分页查询返回VO
 * 基于前端 ViewRecord/index.vue 实际使用字段设计
 * <p>
 * 注意：ipAddress 字段保留，因为前端表格需要显示
 * 移除敏感字段：visitorId, userAgent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 浏览记录ID
    private Long id;

    // 页面标题
    private String pageTitle;

    // 页面路径
    private String pagePath;

    // 访客IP
    private String ipAddress;

    // 来源URL
    private String referer;

    // 浏览时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime viewTime;
}
