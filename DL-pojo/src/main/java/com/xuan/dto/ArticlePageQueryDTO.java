package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章分页查询DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArticlePageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private int page;

    // 每页显示数量
    private int pageSize;

    // 文章标题（模糊搜索）
    private String title;

    // 分类ID
    private Long categoryId;

    // 文章状态：0草稿 1待审核 2已发布 3违规
    private Integer status;
}
