package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户资料修改审核记录 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileAuditVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审核记录 ID */
    private Long id;

    /** 申请人 ID */
    private Long userId;

    /** 申请人用户名 */
    private String username;

    /** 申请人邮箱 */
    private String email;

    /** 申请人当前昵称 */
    private String currentNickname;

    /** 申请人当前头像 */
    private String currentAvatar;

    /** 审核类型：1-昵称 2-头像 */
    private Integer auditType;

    /** 审核类型名称 */
    private String auditTypeName;

    /** 原值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 申请 IP */
    private String applyIp;
}
