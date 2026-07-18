package com.xuan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员登出DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminLogoutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 管理员ID
    private Long id;

    // Token
    private String token;
}
