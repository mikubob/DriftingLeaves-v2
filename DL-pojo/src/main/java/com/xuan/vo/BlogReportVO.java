package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 博客统计报表VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BlogReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 总浏览量
    private Integer viewTotalCount;

    // 今日浏览量
    private Integer viewTodayCount;

    // 总访客数
    private Integer visitorTotalCount;

    // 总文章分类数
    private Integer categoryTotalCount;

    // 总文章标签数
    private Integer tagTotalCount;

    // 总文章数
    private Integer articleTotalCount;
}
