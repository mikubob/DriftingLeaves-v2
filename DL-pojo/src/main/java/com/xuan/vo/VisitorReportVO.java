package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 访客统计VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VisitorReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 日期，以逗号分隔，例如：2025-01-01,2025-01-02
    private String dateList;

    // 新增访客数，以逗号分隔，例如：5,12,8
    private String newVisitorCountList;

    // 累计访客数，以逗号分隔，例如：5,17,25
    private String totalVisitorCountList;
}
