package com.xuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端用户分页查询参数
 */
@Data
public class UserPageQueryDTO {

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "页码必须大于等于 1")
    private Integer page = 1;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小必须大于等于 1")
    @Max(value = 100, message = "每页大小不能超过 100")
    private Integer size = 10;

    /**
     * 关键字：匹配用户名或邮箱（模糊查询）
     */
    private String keyword;

    /**
     * 状态：0 禁用，1 启用
     */
    private Integer status;

    /**
     * 角色编码：过滤拥有该角色的用户
     */
    private String role;
}
