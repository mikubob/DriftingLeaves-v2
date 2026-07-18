package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章标题与浏览量DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArticleTitleViewCountDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章标题
    private String title;

    // 浏览量
    private Integer viewCount;
}
