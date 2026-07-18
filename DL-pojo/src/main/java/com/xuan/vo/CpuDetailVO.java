package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * CPU详情VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CpuDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // CPU使用率
    private BigDecimal cpuPercent;

    // CPU型号
    private String cpuModel;

    // 物理CPU个数
    private Integer physicalPackageCount;

    // 物理核心数
    private Integer physicalCoreCount;

    // 逻辑核心数
    private Integer logicalCoreCount;

    // 各核心使用率列表
    private List<CpuCoreUsageVO> coreUsages;

    // 占用CPU最高的进程列表
    private List<ProcessMetricVO> topProcesses;

    // 采集时间
    private String collectTime;
}
