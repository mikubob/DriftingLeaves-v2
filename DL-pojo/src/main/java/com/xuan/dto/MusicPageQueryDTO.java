package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 音乐分页查询DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MusicPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 页码
    private int page;

    // 每页显示数量
    private int pageSize;

    // 音乐标题
    private String title;

    // 作者
    private String artist;

    // 是否可见
    private Integer isVisible;
}
