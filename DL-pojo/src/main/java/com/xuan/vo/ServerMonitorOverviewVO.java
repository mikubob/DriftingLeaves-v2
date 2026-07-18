package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 服务器监控概览VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServerMonitorOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 负载百分比
    private BigDecimal loadPercent;

    // 负载状态文本
    private String loadStatusText;

    // CPU使用率
    private BigDecimal cpuPercent;

    // CPU核心数
    private Integer cpuCoreCount;

    // 内存已用文本
    private String memoryUsedText;

    // 内存总量文本
    private String memoryTotalText;

    // 内存使用率
    private BigDecimal memoryPercent;

    // 磁盘已用文本
    private String diskUsedText;

    // 磁盘总量文本
    private String diskTotalText;

    // 磁盘使用率
    private BigDecimal diskPercent;

    // 采集时间
    private String collectTime;
}
