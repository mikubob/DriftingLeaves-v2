package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Inode使用情况VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InodeUsageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Inode总数
    private Long total;

    // 已用Inode数
    private Long used;

    // 可用Inode数
    private Long available;

    // Inode使用率
    private BigDecimal usagePercent;
}
