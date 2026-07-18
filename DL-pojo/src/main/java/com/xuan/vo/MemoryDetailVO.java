package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemoryDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private BigDecimal memoryPercent;

    private Long usedBytes;

    private Long totalBytes;

    private Long availableBytes;

    private Long freeBytes;

    private Long sharedBytes;

    private Long bufferCacheBytes;

    private Long swapUsedBytes;

    private Long swapTotalBytes;

    private String usedText;

    private String totalText;

    private String availableText;

    private String freeText;

    private String sharedText;

    private String bufferCacheText;

    private String swapText;

    private List<ProcessMetricVO> topProcesses;

    private String collectTime;
}
