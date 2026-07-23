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
import java.time.LocalDateTime;

/**
 * 用户昵称/头像修改审核
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_profile_audit")
@EqualsAndHashCode(callSuper = true)
public class SysUserProfileAudit extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 申请人ID
    private Long userId;

    // 审核类型：1-昵称 2-头像
    private Integer auditType;

    // 原值
    private String oldValue;

    // 新值
    private String newValue;

    // 状态：0-待审核 1-通过 2-拒绝
    private Integer status;

    // 申请时间
    private LocalDateTime applyTime;

    // 申请IP
    private String applyIp;

    // 审核时间
    private LocalDateTime auditTime;

    // 审核人ID
    private Long auditorId;

    // 审核备注
    private String remark;
}
