package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 个人信息VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PersonalInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 个人信息ID
    private Long id;

    // 昵称
    private String nickname;

    // 标签
    private String tag;

    // 个人简介
    private String description;

    // 头像url
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
