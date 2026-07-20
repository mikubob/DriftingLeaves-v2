package com.xuan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改当前登录用户信息 DTO
 * <p>
 * 供 {@code PUT /admin/me} 接口使用。所有字段可选,只传需要修改的项。
 * </p>
 *
 * <h3>业务规则</h3>
 * <ul>
 *     <li>修改密码:必须同时提供 {@code oldPassword} 和 {@code newPassword},后端校验旧密码</li>
 *     <li>修改邮箱:校验新邮箱唯一性(sys_user.email 已有 UNIQUE KEY)</li>
 *     <li>修改昵称:直接入库</li>
 *     <li>修改密码或邮箱后,建议前端主动重新登录获取新 token(JWT 不可变,旧 token 字段过期)</li>
 * </ul>
 *
 * @author xuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 新昵称(可选)
     */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;

    /**
     * 新邮箱(可选)
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100 个字符")
    private String email;

    /**
     * 旧密码(修改密码时必填)
     */
    @Size(min = 6, max = 50, message = "密码长度应在 6-50 个字符之间")
    private String oldPassword;

    /**
     * 新密码(修改密码时必填)
     */
    @Size(min = 6, max = 50, message = "密码长度应在 6-50 个字符之间")
    private String newPassword;
}
