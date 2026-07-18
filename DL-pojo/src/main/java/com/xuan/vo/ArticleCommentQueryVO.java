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
 * 评论分页查询返回VO
 * 基于前端 Comment/index.vue 实际使用字段设计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCommentQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论ID
    private Long id;

    // 文章ID
    private Long articleId;

    // 文章标题
    private String articleTitle;

    // 根评论ID
    private Long rootId;

    // 回复对象昵称
    private String parentNickname;

    // 评论内容（纯文本）
    private String content;

    // 评论内容（HTML）
    private String contentHtml;

    // 评论用户ID
    private Long userId;

    // 地区
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

    // 审核状态
    private Integer isApproved;

    // 是否管理员回复
    private Integer isAdminReply;

    // 创建时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
