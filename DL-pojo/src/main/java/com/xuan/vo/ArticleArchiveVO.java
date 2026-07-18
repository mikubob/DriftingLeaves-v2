package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文章归档VO（按年月分组）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleArchiveVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // 年份
    private Integer year;

    // 月份
    private Integer month;

    // 文章列表
    private List<ArticleArchiveItemVO> articles;
}
