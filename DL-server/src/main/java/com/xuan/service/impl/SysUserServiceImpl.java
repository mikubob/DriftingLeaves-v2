package com.xuan.service.impl;

import com.xuan.constant.MessageConstant;
import com.xuan.dto.ProfileAuditDTO;
import com.xuan.dto.UpdateMeDTO;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysUserProfileAudit;
import com.xuan.exception.BaseException;
import com.xuan.exception.PasswordErrorException;
import com.xuan.vo.ProfileAuditVO;
import com.xuan.mapper.SysUserMapper;
import com.xuan.mapper.SysUserProfileAuditMapper;
import com.xuan.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
    private final PasswordEncoder passwordEncoder;

    // 审核类型
    private static final int AUDIT_TYPE_NICKNAME = 1;
    private static final int AUDIT_TYPE_AVATAR = 2;

    // 冷却期（天）
    private static final int NICKNAME_COOLDOWN_DAYS = 15;
    private static final int AVATAR_COOLDOWN_DAYS = 30;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMe(Long userId, UpdateMeDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        boolean changed = false;

        // 1. 修改昵称
        if (StringUtils.hasText(dto.getNickname()) && !dto.getNickname().equals(user.getNickname())) {
            user.setNickname(dto.getNickname());
            changed = true;
        }

        // 2. 修改邮箱
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

        // 3. 修改密码
        //    必须同时提供 oldPassword 和 newPassword,否则忽略密码修改
        boolean hasOld = StringUtils.hasText(dto.getOldPassword());
        boolean hasNew = StringUtils.hasText(dto.getNewPassword());
        if (hasOld || hasNew) {
            if (!hasOld || !hasNew) {
                throw new BaseException("修改密码时必须同时提供旧密码和新密码");
            }
            // 校验旧密码(DelegatingPasswordEncoder 会自动识别 {bcrypt} 前缀)
            if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
                throw new PasswordErrorException(MessageConstant.OLD_PASSWORD_ERROR);
            }
            // 新密码不能与旧密码相同
            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new BaseException(MessageConstant.NEW_PASSWORD_NOT_CHANGE);
            }
            // 加密新密码(DelegatingPasswordEncoder 会自动加 {bcrypt} 前缀)
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            changed = true;
        }

        if (!changed) {
            log.info("updateMe 调用但无字段变更: userId={}", userId);
            return;
        }

        sysUserMapper.updateById(user);
        log.info("用户资料更新成功: userId={}, changedFields(nickname={},email={},password={})",
                userId,
                StringUtils.hasText(dto.getNickname()) && !dto.getNickname().equals(user.getNickname()),
                StringUtils.hasText(dto.getEmail()),
                hasOld && hasNew);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyNicknameChange(Long userId, String nickname, String clientIp) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!StringUtils.hasText(nickname)) {
            throw new BaseException("昵称不能为空");
        }
        if (nickname.equals(user.getNickname())) {
            throw new BaseException("新昵称不能与当前昵称相同");
        }

        // 存在待审记录则不允许重复提交
        SysUserProfileAudit pending = auditMapper.selectPendingByUserAndType(userId, AUDIT_TYPE_NICKNAME);
        if (pending != null) {
            throw new BaseException("昵称修改申请正在审核中，请勿重复提交");
        }

        // 账号维度 + IP 维度双锁定
        checkCooldown(userId, clientIp, AUDIT_TYPE_NICKNAME, NICKNAME_COOLDOWN_DAYS, "昵称");

        SysUserProfileAudit audit = SysUserProfileAudit.builder()
                .userId(userId)
                .auditType(AUDIT_TYPE_NICKNAME)
                .oldValue(user.getNickname())
                .newValue(nickname)
                .status(0)
                .applyTime(LocalDateTime.now())
                .applyIp(clientIp)
                .build();
        auditMapper.insert(audit);
        log.info("昵称修改申请已提交待审核: userId={}, nickname={}, ip={}", userId, nickname, clientIp);
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
        LocalDateTime accountLastTime = auditType == AUDIT_TYPE_NICKNAME
                ? sysUserMapper.selectById(userId).getNicknameModifyTime()
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
            if (audit.getAuditType() == AUDIT_TYPE_NICKNAME) {
                user.setNickname(audit.getNewValue());
                user.setNicknameModifyTime(now);
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
            String typeName = audit.getAuditType() == AUDIT_TYPE_NICKNAME ? "昵称" : "头像";
            return ProfileAuditVO.builder()
                    .id(audit.getId())
                    .userId(audit.getUserId())
                    .username(user != null ? user.getUsername() : "")
                    .email(user != null ? user.getEmail() : "")
                    .currentNickname(user != null ? user.getNickname() : "")
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
}
