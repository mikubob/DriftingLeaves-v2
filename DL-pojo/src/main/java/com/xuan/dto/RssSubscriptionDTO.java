package com.xuan.dto;

import jakarta.validation.constraints.Email;
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
 * RSS订阅DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RssSubscriptionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户ID
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    // 昵称（从 sys_user 同步）
    @Size(max = 15, message = "昵称不能超过15字")
    private String nickname;

    // 邮箱（从 sys_user 同步）
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
