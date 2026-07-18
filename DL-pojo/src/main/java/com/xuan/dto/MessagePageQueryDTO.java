package com.xuan.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 留言分页查询DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private Integer page;

    // 每页显示数量
    private Integer pageSize;

    // 是否审核通过，0-否，1-是
    private Integer isApproved;

    // 开始时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    // 结束时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
