package com.xuan.service;

import com.xuan.dto.UpdateMeDTO;

/**
 * 系统用户服务接口
 * <p>
 * 提供当前登录用户的个人资料修改能力(供 {@code PUT /admin/me} 使用)。
 * </p>
 *
 * @author xuan
 */
public interface ISysUserService {

    /**
     * 修改当前登录用户自己的信息
     * <p>
     * 仅允许修改当前登录用户自己的资料(userId 由调用方从 JWT 中提取,不接受前端传入)。
     * </p>
     *
     * <h3>业务规则</h3>
     * <ul>
     *     <li>修改密码:必须同时提供 oldPassword 和 newPassword,后端校验旧密码</li>
     *     <li>修改邮箱:校验新邮箱唯一性(sys_user.email 已有 UNIQUE KEY)</li>
     *     <li>修改昵称:直接入库</li>
     * </ul>
     *
     * @param userId 当前登录用户 ID(从 JWT claim user_id 提取)
     * @param dto    修改参数(字段全部可选)
     */
    void updateMe(Long userId, UpdateMeDTO dto);
}
