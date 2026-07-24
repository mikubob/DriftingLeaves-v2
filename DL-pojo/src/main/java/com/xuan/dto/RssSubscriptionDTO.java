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
 * <p>
 * 用户名与邮箱亦可从 SecurityUser 直接获取，DTO 仅作覆盖可选。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RssSubscriptionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户名（从 sys_user 同步）
    @Size(max = 15, message = "用户名不能超过15字")
    private String username;

    // 邮箱（从 sys_user 同步）
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
