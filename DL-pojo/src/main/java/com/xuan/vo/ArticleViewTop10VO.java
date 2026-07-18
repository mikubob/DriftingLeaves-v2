package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文章访问量排行前十VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArticleViewTop10VO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章标题列表
    private List<String> titleList;

    // 对应文章的浏览量列表
    private List<Integer> viewCountList;
}
