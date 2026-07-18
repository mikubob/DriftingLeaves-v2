package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 管理员ID
    private Long id;

    // 昵称
    private String nickname;

    // 邮箱
    private String email;
}
