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
 * 管理员回复文章评论DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCommentReplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    // 父评论ID
    @NotNull(message = "父评论ID不能为空")
    private Long parentId;

    // 根评论ID
    private Long rootId;

    // 父评论用户名
    @Size(max = 30, message = "父评论用户名不能超过30字")
    private String parentUsername;

    // 回复内容
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容不能超过2000字")
    private String content;

    // 是否使用markdown，0-否，1-是
    private Integer isMarkdown;
}
