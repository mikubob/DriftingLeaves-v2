package com.xuan.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章评论VO（树形结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCommentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论ID
    private Long id;

    // 文章ID
    private Long articleId;

    // 根评论ID
    private Long rootId;

    // 父评论ID
    private Long parentId;

    // 回复对象用户名
    private String parentUsername;

    // 评论内容
    private String content;

    // 评论内容HTML
    private String contentHtml;

    // 是否Markdown
    private Integer isMarkdown;

    // 评论用户ID
    private Long userId;

    // 评论用户名
    private String username;

    // 评论用户头像
    private String avatar;

    // 地区
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

    // 审核状态
    private Integer isApproved;

    // 是否私密
    private Integer isSecret;

    // 是否管理员回复
    private Integer isAdminReply;

    // 创建时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // 子评论列表（仅根评论有值）
    private List<ArticleCommentVO> children;
}
