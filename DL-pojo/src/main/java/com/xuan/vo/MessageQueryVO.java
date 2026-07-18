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
 * 留言分页查询返回VO
 * 基于前端 Message/index.vue 实际使用字段设计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言ID
    private Long id;

    // 根留言ID
    private Long rootId;

    // 回复对象昵称
    private String parentNickname;

    // 留言内容（纯文本）
    private String content;

    // 留言内容（HTML）
    private String contentHtml;

    // 昵称
    private String nickname;

    // 邮箱或QQ
    private String emailOrQq;

    // 地区
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

    // 审核状态
    private Integer isApproved;

    // 创建时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
