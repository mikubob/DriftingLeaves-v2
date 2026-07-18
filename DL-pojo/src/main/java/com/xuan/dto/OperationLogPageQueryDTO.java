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
 * 操作日志分页查询DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OperationLogPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private int page;

    // 每页显示数量
    private int pageSize;

    // 操作用户ID
    private Long userId;

    // 操作类型
    private String operationType;

    // 操作对象
    private String operationTarget;

    // 开始时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    // 结束时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
