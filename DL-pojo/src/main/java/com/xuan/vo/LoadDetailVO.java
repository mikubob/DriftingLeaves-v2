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
 * 系统负载详情VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoadDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 负载百分比
    private BigDecimal loadPercent;

    // 状态文本
    private String statusText;

    // 1分钟平均负载
    private BigDecimal loadAverage1m;

    // 5分钟平均负载
    private BigDecimal loadAverage5m;

    // 15分钟平均负载
    private BigDecimal loadAverage15m;

    // 运行中进程数
    private Integer runningProcessCount;

    // 总进程数
    private Integer totalProcessCount;

    // 用户态CPU占比
    private BigDecimal userPercent;

    //  nice态CPU占比
    private BigDecimal nicePercent;

    // 系统态CPU占比
    private BigDecimal systemPercent;

    // 空闲CPU占比
    private BigDecimal idlePercent;

    // IO等待CPU占比
    private BigDecimal iowaitPercent;

    // 硬中断CPU占比
    private BigDecimal irqPercent;

    // 软中断CPU占比
    private BigDecimal softirqPercent;

    //  steal时间占比
    private BigDecimal stealPercent;

    // guest时间占比
    private BigDecimal guestPercent;

    // guest nice时间占比
    private BigDecimal guestNicePercent;

    // 占用资源最高的进程列表
    private List<ProcessMetricVO> topProcesses;

    // 采集时间
    private String collectTime;
}
