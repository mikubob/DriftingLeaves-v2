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
import java.time.LocalDate;

/**
 * 经历
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("experiences")
@EqualsAndHashCode(callSuper = true)
public class Experiences extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 类型，0-教育经历，1-实习及工作经历，2-项目经历
    private Integer type;

    // 标题，公司名/学校名/项目名
    private String title;

    // 副标题，职位/专业/项目角色
    private String subtitle;

    // logo
    private String logoUrl;

    // 内容
    private String content;

    // 开始时间
    private LocalDate startDate;

    // 结束时间
    private LocalDate endDate;

    // 是否可见
    private Integer isVisible;
}
