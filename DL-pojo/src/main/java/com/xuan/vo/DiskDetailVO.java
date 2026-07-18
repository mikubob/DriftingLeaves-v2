package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 磁盘详情VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DiskDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 资源名称
    private String resourceName;

    // 挂载点
    private String mount;

    // 文件系统
    private String fileSystem;

    // 类型
    private String type;

    // 总容量文本
    private String totalText;

    // 已用容量文本
    private String usedText;

    // 可用容量文本
    private String availableText;

    // 磁盘大小文本
    private String diskSizeText;

    // 使用率
    private BigDecimal usagePercent;

    // Inode使用情况
    private InodeUsageVO inode;

    // 采集时间
    private String collectTime;
}
