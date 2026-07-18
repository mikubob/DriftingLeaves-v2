package com.xuan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员修改密码DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminChangePasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 旧密码
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    // 新密码
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度应在6-32位之间")
    private String newPassword;

    // 确认新密码
    @NotBlank(message = "确认密码不能为空")
    private String confirmNewPassword;
}
