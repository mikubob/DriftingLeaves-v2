package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 每日浏览量统计DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailyViewCountDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 日期
    private LocalDate date;

    // 当日浏览量
    private Integer count;
}
