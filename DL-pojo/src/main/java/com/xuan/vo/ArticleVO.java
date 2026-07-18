package com.xuan.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章列表VO（不含正文内容）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    private Long id;

    // 文章标题
    private String title;

    // URL标识
    private String slug;

    // 文章摘要
    private String summary;

    // 封面图片url
    private String coverImage;

    // 分类ID
    private Long categoryId;

    // 分类名称
    private String categoryName;

    // 浏览次数
    private Long viewCount;

    // 点赞次数
    private Long likeCount;

    // 评论数
    private Long commentCount;

    // 字数统计
    private Long wordCount;

    // 预计阅读时间（分钟）
    private Long readingTime;

    // 文章状态：0草稿 1待审核 2已发布 3违规
    private Integer status;

    // 是否置顶
    private Integer isTop;

    // 发布时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    // 创建时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // 更新时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
