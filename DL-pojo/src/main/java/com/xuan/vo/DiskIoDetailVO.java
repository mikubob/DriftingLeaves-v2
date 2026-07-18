package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 磁盘IO详情VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DiskIoDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 资源名称
    private String resourceName;

    // 每秒读取字节数
    private Long readBytesPerSec;

    // 每秒写入字节数
    private Long writeBytesPerSec;

    // 读取速度文本
    private String readText;

    // 写入速度文本
    private String writeText;

    // 每秒操作数
    private Integer opsPerSec;

    // 平均等待时间（毫秒）
    private Integer awaitMs;

    // 指标点列表
    private List<MetricPointVO> points;

    // 采集时间
    private String collectTime;
}
