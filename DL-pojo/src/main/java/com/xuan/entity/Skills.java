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
 * 技能
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("skills")
@EqualsAndHashCode(callSuper = true)
public class Skills extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 技能名称
    private String name;

    // 技能描述
    private String description;

    // 图标 url
    private String icon;

    // 排序，越小越靠前
    private Integer sort;

    // 是否可见
    private Integer isVisible;
}
