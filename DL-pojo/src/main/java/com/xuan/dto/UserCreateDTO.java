package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端新增用户参数
 * <p>
 * 仅支持邮箱创建，默认禁用，密码由后端随机生成。
 * </p>
 */
@Data
public class UserCreateDTO {

    /**
     * 邮箱，必填且唯一
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /**
     * 用户名，可选。未提供时由后端自动生成。
     */
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    /**
     * 初始密码，必填
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在 6-32 位之间")
    private String password;

    /**
     * 角色编码列表，必填，不能包含 ADMIN
     */
    @NotEmpty(message = "角色不能为空")
    private List<String> roles;
}
