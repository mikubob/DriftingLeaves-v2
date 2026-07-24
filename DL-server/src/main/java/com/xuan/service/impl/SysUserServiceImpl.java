package com.xuan.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuan.constant.MessageConstant;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.ChangePasswordDTO;
import com.xuan.dto.ProfileAuditDTO;
import com.xuan.dto.UpdateMeDTO;
import com.xuan.dto.UserCreateDTO;
import com.xuan.dto.UserPageQueryDTO;
import com.xuan.dto.UserUpdateRolesDTO;
import com.xuan.dto.UserUpdateStatusDTO;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysUserProfileAudit;
import com.xuan.entity.SysUserRole;
import com.xuan.exception.BaseException;
import com.xuan.exception.PasswordErrorException;
import com.xuan.mapper.SysUserMapper;
import com.xuan.mapper.SysUserProfileAuditMapper;
import com.xuan.mapper.SysUserRoleMapper;
import com.xuan.result.PageResult;
import com.xuan.service.ISysUserService;
import com.xuan.util.UsernameGenerator;
import com.xuan.vo.AdminUserVO;
import com.xuan.vo.ProfileAuditVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户服务实现
 * <p>
 * 当前仅提供 {@code PUT /admin/me} 个人资料修改能力。
 * 用户管理 CRUD(分页、新增、修改角色、禁用、重置密码等)本期跳过(P2)。
 * </p>
 *
 * <h3>密码存储约定</h3>
 * <p>
 * 项目使用 DelegatingPasswordEncoder,密码字段以 {@code {bcrypt}} 前缀存储。
 * {@code passwordEncoder.encode(rawPassword)} 会自动加前缀;
 * {@code passwordEncoder.matches(rawPassword, encodedPassword)} 会自动识别前缀。
 * </p>
 *
 * @author xuan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements ISysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserProfileAuditMapper auditMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;

    // 审核类型
    private static final int AUDIT_TYPE_USERNAME = 1;
    private static final int AUDIT_TYPE_AVATAR = 2;

    // 冷却期（天）
    private static final int USERNAME_COOLDOWN_DAYS = 15;
    private static final int AVATAR_COOLDOWN_DAYS = 30;
    private static final int PASSWORD_COOLDOWN_DAYS = 15;

    // 不允许通过管理端分配的角色
    private static final Set<String> NON_ASSIGNABLE_ROLES = Set.of("ADMIN");

    // 新增用户默认登录类型：本地
    private static final int LOGIN_TYPE_LOCAL = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMe(Long userId, UpdateMeDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        boolean changed = false;

        // 1. 修改邮箱
        //    sys_user.email 已有 UNIQUE KEY,且 NULL 不参与唯一性检查(从项目 memory 得知)
        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(user.getEmail())) {
            // 校验新邮箱是否已被其他用户占用
            SysUser existing = sysUserMapper.selectByEmail(dto.getEmail());
            if (existing != null && !existing.getId().equals(userId)) {
                throw new BaseException(MessageConstant.EMAIL_EXISTS);
            }
            user.setEmail(dto.getEmail());
            changed = true;
        }

        // 2. 修改密码
        //    必须同时提供 oldPassword 和 newPassword,否则忽略密码修改
        boolean hasOld = StringUtils.hasText(dto.getOldPassword());
        boolean hasNew = StringUtils.hasText(dto.getNewPassword());
        boolean passwordChanged = false;
        if (hasOld || hasNew) {
            if (!hasOld || !hasNew) {
                throw new BaseException("修改密码时必须同时提供旧密码和新密码");
            }
            updatePassword(user, dto.getOldPassword(), dto.getNewPassword());
            changed = true;
            passwordChanged = true;
        }

        if (!changed) {
            log.info("updateMe 调用但无字段变更: userId={}", userId);
            return;
        }

        sysUserMapper.updateById(user);
        log.info("用户资料更新成功: userId={}, changedFields(email={},password={})",
                userId,
                StringUtils.hasText(dto.getEmail()),
                passwordChanged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 新密码与确认密码一致性校验
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BaseException("两次输入的新密码不一致");
        }

        updatePassword(user, dto.getOldPassword(), dto.getNewPassword());
        sysUserMapper.updateById(user);
        log.info("用户密码修改成功: userId={}", userId);
    }

    /**
     * 统一密码更新逻辑（含 15 天冷却、旧密码校验、新旧密码差异校验）
     */
    private void updatePassword(SysUser user, String oldPassword, String newPassword) {
        // 1. 冷却期校验
        LocalDateTime lastModifyTime = user.getPasswordModifyTime();
        if (lastModifyTime != null) {
            long days = ChronoUnit.DAYS.between(lastModifyTime, LocalDateTime.now());
            if (days < PASSWORD_COOLDOWN_DAYS) {
                throw new BaseException(String.format("同一账号 %d 天内只能修改一次密码，请 %d 天后再试",
                        PASSWORD_COOLDOWN_DAYS, PASSWORD_COOLDOWN_DAYS - days));
            }
        }

        // 2. 校验旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.OLD_PASSWORD_ERROR);
        }

        // 3. 新密码不能与旧密码相同
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BaseException(MessageConstant.NEW_PASSWORD_NOT_CHANGE);
        }

        // 4. 加密并更新密码及修改时间
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordModifyTime(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyUsernameChange(Long userId, String username, String clientIp) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!StringUtils.hasText(username)) {
            throw new BaseException("用户名不能为空");
        }
        if (username.equals(user.getUsername())) {
            throw new BaseException("新用户名不能与当前用户名相同");
        }

        // 存在待审记录则不允许重复提交
        SysUserProfileAudit pending = auditMapper.selectPendingByUserAndType(userId, AUDIT_TYPE_USERNAME);
        if (pending != null) {
            throw new BaseException("用户名修改申请正在审核中，请勿重复提交");
        }

        // 账号维度 + IP 维度双锁定
        checkCooldown(userId, clientIp, AUDIT_TYPE_USERNAME, USERNAME_COOLDOWN_DAYS, "用户名");

        SysUserProfileAudit audit = SysUserProfileAudit.builder()
                .userId(userId)
                .auditType(AUDIT_TYPE_USERNAME)
                .oldValue(user.getUsername())
                .newValue(username)
                .status(0)
                .applyTime(LocalDateTime.now())
                .applyIp(clientIp)
                .build();
        auditMapper.insert(audit);
        log.info("用户名修改申请已提交待审核: userId={}, username={}, ip={}", userId, username, clientIp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAvatarChange(Long userId, String avatar, String clientIp) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!StringUtils.hasText(avatar)) {
            throw new BaseException("头像不能为空");
        }
        if (avatar.equals(user.getAvatar())) {
            throw new BaseException("新头像不能与当前头像相同");
        }

        // 存在待审记录则不允许重复提交
        SysUserProfileAudit pending = auditMapper.selectPendingByUserAndType(userId, AUDIT_TYPE_AVATAR);
        if (pending != null) {
            throw new BaseException("头像修改申请正在审核中，请勿重复提交");
        }

        // 账号维度 + IP 维度双锁定
        checkCooldown(userId, clientIp, AUDIT_TYPE_AVATAR, AVATAR_COOLDOWN_DAYS, "头像");

        SysUserProfileAudit audit = SysUserProfileAudit.builder()
                .userId(userId)
                .auditType(AUDIT_TYPE_AVATAR)
                .oldValue(user.getAvatar())
                .newValue(avatar)
                .status(0)
                .applyTime(LocalDateTime.now())
                .applyIp(clientIp)
                .build();
        auditMapper.insert(audit);
        log.info("头像修改申请已提交待审核: userId={}, ip={}", userId, clientIp);
    }

    /**
     * 双锁定校验：账号维度 + IP 维度
     */
    private void checkCooldown(Long userId, String clientIp, int auditType, int cooldownDays, String typeName) {
        LocalDateTime now = LocalDateTime.now();

        // 账号维度
        LocalDateTime accountLastTime = auditType == AUDIT_TYPE_USERNAME
                ? sysUserMapper.selectById(userId).getUsernameModifyTime()
                : sysUserMapper.selectById(userId).getAvatarModifyTime();
        if (accountLastTime != null) {
            long days = ChronoUnit.DAYS.between(accountLastTime, now);
            if (days < cooldownDays) {
                throw new BaseException(String.format("同一账号 %d 天内只能修改一次%s，请 %d 天后再试",
                        cooldownDays, typeName, cooldownDays - days));
            }
        }

        // IP 维度
        if (StringUtils.hasText(clientIp) && !"unknown".equalsIgnoreCase(clientIp)) {
            SysUserProfileAudit ipLastAudit = auditMapper.selectLatestApprovedByIp(clientIp, auditType);
            if (ipLastAudit != null && ipLastAudit.getAuditTime() != null) {
                long days = ChronoUnit.DAYS.between(ipLastAudit.getAuditTime(), now);
                if (days < cooldownDays) {
                    throw new BaseException(String.format("同一 IP %d 天内只能修改一次%s，请 %d 天后再试",
                            cooldownDays, typeName, cooldownDays - days));
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditProfileChange(ProfileAuditDTO dto, Long auditorId) {
        SysUserProfileAudit audit = auditMapper.selectById(dto.getAuditId());
        if (audit == null) {
            throw new BaseException("审核记录不存在");
        }
        if (audit.getStatus() != 0) {
            throw new BaseException("该申请已处理，请勿重复操作");
        }
        if (dto.getStatus() != 1 && dto.getStatus() != 2) {
            throw new BaseException("审核结果参数错误");
        }

        LocalDateTime now = LocalDateTime.now();
        audit.setStatus(dto.getStatus());
        audit.setAuditTime(now);
        audit.setAuditorId(auditorId);
        audit.setRemark(dto.getRemark());
        auditMapper.updateById(audit);

        if (dto.getStatus() == 1) {
            // 审核通过：更新 sys_user
            SysUser user = sysUserMapper.selectById(audit.getUserId());
            if (user == null) {
                throw new BaseException("申请人不存在");
            }
            if (audit.getAuditType() == AUDIT_TYPE_USERNAME) {
                user.setUsername(audit.getNewValue());
                user.setUsernameModifyTime(now);
            } else if (audit.getAuditType() == AUDIT_TYPE_AVATAR) {
                user.setAvatar(audit.getNewValue());
                user.setAvatarModifyTime(now);
                user.setLastAvatarModifyIp(audit.getApplyIp());
            }
            sysUserMapper.updateById(user);
            log.info("资料修改审核通过: auditId={}, userId={}, type={}",
                    audit.getId(), audit.getUserId(), audit.getAuditType());
        } else {
            log.info("资料修改审核拒绝: auditId={}, userId={}, type={}, remark={}",
                    audit.getId(), audit.getUserId(), audit.getAuditType(), dto.getRemark());
        }
    }

    @Override
    public List<ProfileAuditVO> listPendingProfileAudits() {
        List<SysUserProfileAudit> audits = auditMapper.selectPendingList();
        if (audits.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = audits.stream()
                .map(SysUserProfileAudit::getUserId)
                .distinct()
                .toList();
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        return audits.stream().map(audit -> {
            SysUser user = userMap.get(audit.getUserId());
            String typeName = audit.getAuditType() == AUDIT_TYPE_USERNAME ? "用户名" : "头像";
            return ProfileAuditVO.builder()
                    .id(audit.getId())
                    .userId(audit.getUserId())
                    .username(user != null ? user.getUsername() : "")
                    .email(user != null ? user.getEmail() : "")
                    .currentUsername(user != null ? user.getUsername() : "")
                    .currentAvatar(user != null ? user.getAvatar() : "")
                    .auditType(audit.getAuditType())
                    .auditTypeName(typeName)
                    .oldValue(audit.getOldValue())
                    .newValue(audit.getNewValue())
                    .applyTime(audit.getApplyTime())
                    .applyIp(audit.getApplyIp())
                    .build();
        }).toList();
    }

    @Override
    public PageResult<AdminUserVO> pageUsers(UserPageQueryDTO dto) {
        Page<Map<String, Object>> page = new Page<>(dto.getPage(), dto.getSize());
        IPage<Map<String, Object>> result = sysUserMapper.selectUserPage(
                page, dto.getKeyword(), dto.getStatus(), dto.getRole());

        List<AdminUserVO> records = result.getRecords().stream()
                .map(this::mapToAdminUserVO)
                .toList();

        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private AdminUserVO mapToAdminUserVO(Map<String, Object> map) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(((Number) map.get("id")).longValue());
        vo.setUsername((String) map.get("username"));
        vo.setEmail((String) map.get("email"));
        vo.setAvatar((String) map.get("avatar"));
        vo.setStatus(((Number) map.get("status")).intValue());
        vo.setLoginType(((Number) map.get("login_type")).intValue());
        vo.setUserType(((Number) map.get("user_type")).intValue());
        vo.setLastLoginTime((LocalDateTime) map.get("last_login_time"));
        vo.setLastLoginIp((String) map.get("last_login_ip"));
        vo.setCreateTime((LocalDateTime) map.get("create_time"));
        vo.setUpdateTime((LocalDateTime) map.get("update_time"));
        String rolesStr = (String) map.get("roles");
        vo.setRoles(StringUtils.hasText(rolesStr)
                ? Arrays.asList(rolesStr.split(","))
                : List.of());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateDTO dto, Long operator) {
        validateAssignableRoles(dto.getRoles());

        // 邮箱唯一性校验
        SysUser existByEmail = sysUserMapper.selectByEmail(dto.getEmail());
        if (existByEmail != null) {
            throw new BaseException(MessageConstant.EMAIL_EXISTS);
        }

        // 用户名处理
        String username = StringUtils.hasText(dto.getUsername())
                ? dto.getUsername().trim()
                : usernameGenerator.generate();
        SysUser existByUsername = sysUserMapper.selectByUsername(username);
        if (existByUsername != null) {
            throw new BaseException(MessageConstant.USERNAME_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        SysUser user = SysUser.builder()
                .username(username)
                .email(dto.getEmail())
                .password(encodedPassword)
                .status(StatusConstant.DISABLE)
                .loginType(LOGIN_TYPE_LOCAL)
                .userType(1)
                .build();
        sysUserMapper.insert(user);

        bindRoles(user.getId(), dto.getRoles());

        log.info("管理员新增用户: operator={}, userId={}, email={}, username={}",
                operator, user.getId(), dto.getEmail(), username);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, Integer status, Long operator) {
        if (userId.equals(operator)) {
            throw new BaseException("不能操作自己的账号");
        }
        if (status == null || (!status.equals(StatusConstant.ENABLE) && !status.equals(StatusConstant.DISABLE))) {
            throw new BaseException("状态参数错误");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        sysUserMapper.updateById(update);

        log.info("管理员修改用户状态: operator={}, userId={}, status={}", operator, userId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRoles(Long userId, UserUpdateRolesDTO dto, Long operator) {
        if (userId.equals(operator)) {
            throw new BaseException("不能操作自己的账号");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        validateAssignableRoles(dto.getRoles());
        bindRoles(userId, dto.getRoles());

        log.info("管理员修改用户角色: operator={}, userId={}, roles={}", operator, userId, dto.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long operator) {
        if (userId.equals(operator)) {
            throw new BaseException("不能删除自己的账号");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        sysUserMapper.deleteById(userId);
        log.info("管理员删除用户: operator={}, userId={}", operator, userId);
    }

    /**
     * 校验可分配角色：不能包含 ADMIN，且必须都是已存在的角色
     */
    private void validateAssignableRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BaseException("角色不能为空");
        }
        for (String role : roles) {
            if (!StringUtils.hasText(role)) {
                throw new BaseException("角色编码不能为空");
            }
            String upperRole = role.toUpperCase();
            if (NON_ASSIGNABLE_ROLES.contains(upperRole)) {
                throw new BaseException("不允许分配管理员角色");
            }
            if (sysUserRoleMapper.selectRoleIdByCode(upperRole) == null) {
                throw new BaseException("角色不存在: " + role);
            }
        }
    }

    /**
     * 绑定用户角色：先删除旧角色，再插入新角色
     */
    private void bindRoles(Long userId, List<String> roles) {
        sysUserRoleMapper.deleteByUserId(userId);
        for (String role : roles) {
            Long roleId = sysUserRoleMapper.selectRoleIdByCode(role.toUpperCase());
            if (roleId == null) {
                throw new BaseException("角色不存在: " + role);
            }
            SysUserRole userRole = SysUserRole.builder()
                    .userId(userId)
                    .roleId(roleId)
                    .build();
            sysUserRoleMapper.insert(userRole);
        }
    }
}
