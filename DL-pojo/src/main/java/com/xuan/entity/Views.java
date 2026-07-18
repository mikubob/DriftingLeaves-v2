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
 * 浏览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("views")
public class Views implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 访客ID
    private Long visitorId;

    // 页面路径
    private String pagePath;

    // 来源URL
    private String referer;

    // 页面标题
    private String pageTitle;

    // IP地址
    private String ipAddress;

    // 用户代理
    private String userAgent;

    // 访问时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime viewTime;
}
