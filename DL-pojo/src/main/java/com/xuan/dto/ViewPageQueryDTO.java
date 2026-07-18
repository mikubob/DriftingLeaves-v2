package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 页面浏览记录分页查询DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ViewPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private int page;

    // 每页显示数量
    private int pageSize;

    // 页面路径
    private String pagePath;

    // 来源URL
    private String referer;

    // 访客ID
    private Long visitorId;
}
