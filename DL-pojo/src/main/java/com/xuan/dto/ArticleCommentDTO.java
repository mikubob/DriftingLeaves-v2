package com.xuan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 博客端访客提交文章评论DTO
 * <p>
 * 阶段四：移除 userId 字段，userId 由 Controller 从 SecurityContext 中获取后传入 Service。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCommentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    // 根评论ID
    private Long rootId;

    // 父评论ID
    private Long parentId;

    // 父评论昵称
    @Size(max = 15, message = "父评论昵称不能超过15字")
    private String parentNickname;

    // 评论内容
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过2000字")
    private String content;

    // 是否使用markdown
    private Integer isMarkdown;

    // 是否悄悄话
    private Integer isSecret;

    // 有回复是否通知
    private Integer isNotice;
}
