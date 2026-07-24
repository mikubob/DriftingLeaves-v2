package com.xuan.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 管理端修改用户角色参数
 * <p>
 * 不允许分配 ADMIN 角色。
 * </p>
 */
@Data
public class UserUpdateRolesDTO {

    /**
     * 角色编码列表，必填，不能包含 ADMIN
     */
    @NotEmpty(message = "角色不能为空")
    private List<String> roles;
}
