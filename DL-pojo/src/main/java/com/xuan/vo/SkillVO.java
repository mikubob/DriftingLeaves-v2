package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 技能VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 技能ID
    private Long id;

    // 技能名称
    private String name;

    // 技能描述
    private String description;

    // 图标url
    private String icon;

    // 排序，越小越靠前
    private Integer sort;

}
