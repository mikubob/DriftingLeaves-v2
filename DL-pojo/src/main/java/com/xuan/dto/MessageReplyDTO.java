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
 * 管理员回复留言DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 父留言ID
    @NotNull(message = "父留言ID不能为空")
    private Long parentId;

    // 根留言ID
    private Long rootId;

    // 父留言用户名
    @Size(max = 30, message = "父留言用户名不能超过30字")
    private String parentUsername;

    // 回复内容
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容不能超过2000字")
    private String content;

    // 是否使用markdown，0-否，1-是
    private Integer isMarkdown;
}
