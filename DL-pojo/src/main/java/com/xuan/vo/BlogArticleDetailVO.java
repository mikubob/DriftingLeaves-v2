package com.xuan.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 博客端文章详情VO（含HTML内容）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogArticleDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 文章ID
    private Long id;

    // 标题
    private String title;

    // 别名
    private String slug;

    // 摘要
    private String summary;

    // 封面图片
    private String coverImage;

    // HTML内容
    private String contentHtml;

    // Markdown内容
    private String contentMarkdown;

    // 分类ID
    private Long categoryId;

    // 分类名称
    private String categoryName;

    // 浏览量
    private Long viewCount;

    // 点赞数
    private Long likeCount;

    // 评论数
    private Long commentCount;

    // 字数
    private Long wordCount;

    // 阅读时长
    private Long readingTime;

    // 发布时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    // 更新时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // 文章标签名称列表
    private List<String> tagNames;

    // 上一篇
    private BlogArticleVO prevArticle;

    // 下一篇
    private BlogArticleVO nextArticle;

    // 相关文章推荐
    private List<BlogArticleVO> relatedArticles;
}
