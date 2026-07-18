package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 浏览量统计VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ViewReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 日期，以逗号分隔，例如：2025-01-01,2025-01-02
    private String dateList;

    // 浏览量，以逗号分隔，例如：120,350,200
    private String viewCountList;
}
