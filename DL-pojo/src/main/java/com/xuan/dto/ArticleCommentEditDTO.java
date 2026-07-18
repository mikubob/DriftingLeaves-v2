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
 * 访客编辑评论DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCommentEditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 评论ID
    @NotNull(message = "评论ID不能为空")
    private Long id;

    // 用户ID（用于验证身份）
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    // 编辑后的内容
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过2000字")
    private String content;

    // 是否使用markdown
    private Integer isMarkdown;
}
