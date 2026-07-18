package com.xuan.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xuan.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 音乐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("music")
@EqualsAndHashCode(callSuper = true)
public class Music extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 音乐标题
    private String title;

    // 作者
    private String artist;

    // 时长，单位：秒
    private Integer duration;

    // 封面图片 url
    private String coverImage;

    // 音频文件 url
    private String musicUrl;

    // 歌词文件 url
    private String lyricUrl;

    // 是否有歌词，0-否，1-是
    private Integer hasLyric;

    // 歌词类型,lrc,json,txt
    private String lyricType;

    // 排序，越小越靠前
    private Integer sort;

    // 是否可见
    private Integer isVisible;
}
