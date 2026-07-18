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
 * 个人信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("personal_info")
@EqualsAndHashCode(callSuper = true)
public class PersonalInfo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 昵称
    private String nickname;

    // 标签
    private String tag;

    // 个人简介
    private String description;

    // 头像 url
    private String avatar;

    // 个人网站
    private String website;

    // 电子邮箱
    private String email;

    // GitHub
    private String github;

    // 所在地
    private String location;
}
