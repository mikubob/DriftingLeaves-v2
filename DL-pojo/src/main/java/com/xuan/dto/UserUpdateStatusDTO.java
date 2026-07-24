package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端修改用户状态参数
 */
@Data
public class UserUpdateStatusDTO {

    /**
     * 状态：0 禁用，1 启用
     */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
