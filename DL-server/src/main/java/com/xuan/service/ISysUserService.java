package com.xuan.service;

import com.xuan.dto.ProfileAuditDTO;
import com.xuan.dto.UpdateMeDTO;
import com.xuan.dto.UserCreateDTO;
import com.xuan.dto.UserPageQueryDTO;
import com.xuan.dto.UserUpdateRolesDTO;
import com.xuan.dto.UserUpdateStatusDTO;
import com.xuan.result.PageResult;
import com.xuan.vo.AdminUserVO;
import com.xuan.vo.ProfileAuditVO;

import java.util.List;

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
 * </ul>
 *
 * @param userId 当前登录用户 ID(从 JWT claim user_id 提取)
 * @param dto    修改参数(字段全部可选)
 */
void updateMe(Long userId, UpdateMeDTO dto);

/**
 * 申请修改用户名
 * <p>
 * 提交后进入待审核状态，需管理员审核通过后才更新 sys_user.username。
 * 同一账号 / 同一 IP 15 天内只能申请一次。
 * </p>
 *
 * @param userId   当前登录用户 ID
 * @param username 新用户名
 * @param clientIp 申请 IP
 */
void applyUsernameChange(Long userId, String username, String clientIp);

    /**
     * 申请修改头像
     * <p>
     * 提交后进入待审核状态，需管理员审核通过后才更新 sys_user.avatar。
     * 同一账号 / 同一 IP 30 天内只能申请一次。
     * </p>
     *
     * @param userId   当前登录用户 ID
     * @param avatar   新头像 URL
     * @param clientIp 申请 IP
     */
    void applyAvatarChange(Long userId, String avatar, String clientIp);

    /**
     * 管理员审核用户名/头像修改申请
     *
     * @param dto        审核参数
     * @param auditorId  当前管理员用户 ID
     */
    void auditProfileChange(ProfileAuditDTO dto, Long auditorId);

    /**
     * 查询所有待审核的用户名/头像修改申请
     *
     * @return 待审核记录列表，包含申请人当前信息
     */
    List<ProfileAuditVO> listPendingProfileAudits();

    /**
     * 管理端分页查询用户列表
     *
     * @param dto 分页查询参数
     * @return 用户分页结果
     */
    PageResult<AdminUserVO> pageUsers(UserPageQueryDTO dto);

    /**
     * 管理端新增用户
     * <p>
     * 仅支持邮箱创建，密码由管理员传入，默认禁用状态。
     * </p>
     *
     * @param dto      新增参数
     * @param operator 操作人用户 ID
     * @return 新增用户的 ID
     */
    Long createUser(UserCreateDTO dto, Long operator);

    /**
     * 管理端修改用户状态（启用/禁用）
     *
     * @param userId   目标用户 ID
     * @param status   状态：0 禁用，1 启用
     * @param operator 操作人用户 ID
     */
    void updateUserStatus(Long userId, Integer status, Long operator);

    /**
     * 管理端修改用户角色
     * <p>
     * 只能分配非 ADMIN 角色。
     * </p>
     *
     * @param userId   目标用户 ID
     * @param dto      角色参数
     * @param operator 操作人用户 ID
     */
    void updateUserRoles(Long userId, UserUpdateRolesDTO dto, Long operator);

    /**
     * 管理端删除用户
     *
     * @param userId   目标用户 ID
     * @param operator 操作人用户 ID
     */
    void deleteUser(Long userId, Long operator);
}
