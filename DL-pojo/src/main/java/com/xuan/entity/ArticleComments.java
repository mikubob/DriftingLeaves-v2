package com.xuan.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xuan.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 文章评论
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_comments")
@EqualsAndHashCode(callSuper = true)
public class ArticleComments extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 文章 ID
    private Long articleId;

    // 根评论 ID,null 是一级评论
    private Long rootId;

    // 父评论 ID,null 是一级评论
    private Long parentId;

    // 父评论昵称
    private String parentNickname;

    // 评论内容
    private String content;

    // 转换后的 HTML 内容
    private String contentHtml;

    // 评论用户ID
    private Long userId;

    // 地址
    private String location;

    // 操作系统名称
    private String userAgentOs;

    // 浏览器名称
    private String userAgentBrowser;

    // 是否审核通过，0-否，1-是
    private Integer isApproved;

    // 是否使用 markdown，0-否，1-是
    private Integer isMarkdown;

    // 是否悄悄话：0-否，1-是（仅博主和评论者可见）
    private Integer isSecret;

    // 有回复是否通知，0-否，1-是
    private Integer isNotice;

    // 是否编辑过，0-否，1-是
    private Integer isEdited;

    // 是否为管理员回复，0-否，1-是
    private Integer isAdminReply;

    // 文章标题（非数据库字段，关联查询时填充）
    @TableField(exist = false)
    private String articleTitle;
}
