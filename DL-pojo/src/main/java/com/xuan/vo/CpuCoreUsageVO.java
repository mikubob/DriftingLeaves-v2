package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * CPU核心使用率VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CpuCoreUsageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 核心标签
    private String coreLabel;

    // 使用率
    private BigDecimal usagePercent;
}
