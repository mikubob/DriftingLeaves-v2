package com.xuan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员修改昵称DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminChangeNicknameDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 昵称
    @NotBlank(message = "昵称不能为空")
    @Size(max = 30, message = "昵称不能超过30字")
    private String nickname;
}
