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
 * 申请修改昵称 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyNicknameDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;
}
