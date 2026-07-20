package com.xuan.service.impl;

import com.xuan.constant.MessageConstant;
import com.xuan.dto.UpdateMeDTO;
import com.xuan.entity.SysUser;
import com.xuan.exception.BaseException;
import com.xuan.exception.PasswordErrorException;
import com.xuan.mapper.SysUserMapper;
import com.xuan.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final PasswordEncoder passwordEncoder;

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
}
