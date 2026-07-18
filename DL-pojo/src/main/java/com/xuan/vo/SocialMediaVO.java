package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 社交媒体VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SocialMediaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 社交媒体ID
    private Long id;

    // 名称
    private String name;

    // 图标类名
    private String icon;

    // 链接
    private String link;

    // 排序，越小越靠前
    private Integer sort;
}
