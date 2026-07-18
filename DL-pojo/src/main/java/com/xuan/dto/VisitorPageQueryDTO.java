package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 访客分页查询DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VisitorPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private int page;

    // 每页显示数量
    private int pageSize;

    // 国家
    private String country;

    // 省份
    private String province;

    // 城市
    private String city;

    // 状态,是否被封禁 0正常 1封禁
    private Integer status;
}
