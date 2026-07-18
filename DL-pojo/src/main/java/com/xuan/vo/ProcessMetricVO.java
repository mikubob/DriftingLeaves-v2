package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 进程指标VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcessMetricVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 进程ID
    private Long pid;

    // 进程名称
    private String processName;

    // CPU使用率
    private BigDecimal cpuPercent;

    // 内存使用率
    private BigDecimal memoryPercent;
}
