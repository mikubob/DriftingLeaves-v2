package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员修改邮箱DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminChangeEmailDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 邮箱
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    // 验证码
    @NotBlank(message = "验证码不能为空")
    private String code;
}
