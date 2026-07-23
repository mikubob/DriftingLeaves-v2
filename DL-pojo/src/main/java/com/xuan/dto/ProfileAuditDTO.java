package com.xuan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员审核用户昵称/头像修改申请 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileAuditDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审核记录ID
     */
    @NotNull(message = "审核记录ID不能为空")
    private Long auditId;

    /**
     * 审核结果：1-通过 2-拒绝
     */
    @NotNull(message = "审核结果不能为空")
    private Integer status;

    /**
     * 审核备注
     */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;
}
