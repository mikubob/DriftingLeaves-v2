package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 友情链接VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendLinkVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 友情链接ID
    private Long id;

    // 网站名称
    private String name;

    // 网站地址
    private String url;

    // 头像url
    private String avatarUrl;

    // 网站描述
    private String description;

    // 排序
    private Integer sort;
}
