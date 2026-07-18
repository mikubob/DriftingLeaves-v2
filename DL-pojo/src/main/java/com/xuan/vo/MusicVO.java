package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 音乐VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MusicVO implements Serializable {

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
}
