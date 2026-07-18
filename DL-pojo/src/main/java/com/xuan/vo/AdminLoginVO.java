package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员登录VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminLoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 管理员ID
    private Long id;

    // 登录令牌（仅用于服务端写入 Cookie，不返回给前端）
    private String token;
}
