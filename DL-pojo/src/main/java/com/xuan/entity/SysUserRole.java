package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户角色关联
 * <p>
 * 注意:本表为纯关联表,只有 id/user_id/role_id 三列,没有 create_time/update_time,
 * 因此不继承 {@link com.xuan.entity.base.BaseEntity},避免 MyBatis-Plus 自动填充
 * 触发 {@code Unknown column 'create_time'} 错误。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_role")
@EqualsAndHashCode(callSuper = false)
public class SysUserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 角色ID
    private Long roleId;
}
