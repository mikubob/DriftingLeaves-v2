package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 音乐分页查询返回VO
 * 基于前端 Music/index.vue 实际使用字段设计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 音乐ID
    private Long id;

    // 音乐标题
    private String title;

    // 作者
    private String artist;

    // 时长，单位：秒
    private Integer duration;

    // 封面图片url
    private String coverImage;

    // 音频文件url
    private String musicUrl;

    // 歌词文件url
    private String lyricUrl;

    // 是否有歌词，0-否，1-是
    private Integer hasLyric;

    // 歌词类型,lrc,json,txt
    private String lyricType;

    // 排序
    private Integer sort;

    // 是否可见
    private Integer isVisible;
}
