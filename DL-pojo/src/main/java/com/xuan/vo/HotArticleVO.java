package com.xuan.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 热门文章 VO
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HotArticleVO extends BlogArticleVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前榜单排序值
     */
    private Long hotValue;
}
