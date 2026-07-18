package com.xuan.entity;

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
 * 社交媒体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("social_media")
@EqualsAndHashCode(callSuper = true)
public class SocialMedia extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 名称
    private String name;

    // 图标类名
    private String icon;

    // 链接
    private String link;

    // 排序，越小越靠前
    private Integer sort;

    // 是否可见
    private Integer isVisible;
}
