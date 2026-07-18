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
 * 访客提交留言DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言内容
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 2000, message = "留言内容不能超过2000字")
    private String content;

    // 根留言ID,null是一级留言
    private Long rootId;

    // 父留言ID,null是一级留言
    private Long parentId;

    // 父留言昵称
    @Size(max = 15, message = "父留言昵称不能超过15字")
    private String parentNickname;

    // 留言用户ID
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    // 是否使用markdown，0-否，1-是
    private Integer isMarkdown;

    // 是否悄悄话，0-否，1-是
    private Integer isSecret;

    // 有回复是否通知，0-否，1-是
    private Integer isNotice;
}
