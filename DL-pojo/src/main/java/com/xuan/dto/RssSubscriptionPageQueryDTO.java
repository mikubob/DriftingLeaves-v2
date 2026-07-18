package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * RSS订阅分页查询DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RssSubscriptionPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private int page;

    // 每页显示数量
    private int pageSize;

    // 邮箱
    private String email;

    // 是否激活，0-否，1-是
    private Integer isActive;
}
