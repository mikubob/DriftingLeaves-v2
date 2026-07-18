package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 指标点VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricPointVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 时间
    private String time;

    // 输入值
    private BigDecimal inValue;

    // 输出值
    private BigDecimal outValue;

    // 读取值
    private BigDecimal readValue;

    // 写入值
    private BigDecimal writeValue;
}
