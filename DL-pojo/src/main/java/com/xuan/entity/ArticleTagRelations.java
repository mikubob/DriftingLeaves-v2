package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章 - 标签关联
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_tag_relations")
public class ArticleTagRelations implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 文章ID
    private Long articleId;

    // 标签ID
    private Long tagId;
}
