package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * 文章分类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_categories")
@EqualsAndHashCode(callSuper = true)
public class ArticleCategories extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 分类名称
    private String name;

    // URL 标识
    private String slug;

    // 分类描述
    private String description;

    // 排序，越小越靠前
    private Integer sort;

    // 文章数量（非数据库字段，查询时计算）
    @TableField(exist = false)
    private Integer articleCount;
}
