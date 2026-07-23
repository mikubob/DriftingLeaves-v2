package com.xuan.service.impl;

import com.xuan.constant.MessageConstant;
import com.xuan.dto.RegisterDTO;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysUserRole;
import com.xuan.exception.BaseException;
import com.xuan.exception.VerifyCodeCoolDownException;
import com.xuan.exception.VerifyCodeErrorException;
import com.xuan.exception.VerifyCodeLockException;
import com.xuan.mapper.SysUserMapper;
import com.xuan.mapper.SysUserRoleMapper;
import com.xuan.service.AsyncEmailService;
import com.xuan.service.BlogUserService;
import com.xuan.service.EmailCodeService;
import com.xuan.util.RoleNicknameMapper;
import com.xuan.util.UsernameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 博客端用户服务实现（注册 + 发送验证码）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogUserServiceImpl implements BlogUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final EmailCodeService emailCodeService;
    private final AsyncEmailService asyncEmailService;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;

    /**
     * 开发模式固定验证码，仅在本地联调时使用
     */
    @Value("${dl.security.dev-code:}")
    private String devCode;

    /**
     * 发送邮箱验证码
     * <p>
     * 流程：频率限制 → 锁定检查 → 生成验证码 → 异步发送邮件 → 保存到 Redis
     * </p>
     */
    @Override
    public void sendEmailCode(String email) {
        // 1. 锁定检查
        if (emailCodeService.isLocked(email)) {
            Long minutes = emailCodeService.getLockRemainingMinutes(email);
            throw new VerifyCodeLockException(
                    MessageConstant.EMAIL_VERIFY_CODE_LOCKED + minutes + " 分钟");
        }

        // 2. 频率限制
        if (!emailCodeService.canSendCode(email)) {
            Long seconds = emailCodeService.getRemainingCooldown(email);
            throw new VerifyCodeCoolDownException(
                    MessageConstant.EMAIL_VERIFY_CODE_COOLDOWN + "（剩余 " + seconds + " 秒）");
        }

        // 3. 生成验证码
        String code = emailCodeService.generateCode();

        // 4. 保存到 Redis（含频率限制、尝试次数重置）
        emailCodeService.saveCode(email, code);

        // 5. 异步发送邮件
        //    EmailService.sendVerifyCode 是同步 SMTP 调用，可能阻塞 1-3s，因此走异步
        //    注意：注册流程下用户尚未登录，无 userId，仅传 email
        asyncEmailService.sendVerifyCodeAsync(email, code);

        log.info("博客端发送邮箱验证码成功: email={}", email);
    }

    /**
     * 用户注册
     * <p>
     * 流程：唯一性校验 → 验证码校验 → 创建 sys_user → 关联 GUEST 角色
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        String email = registerDTO.getEmail();
        String code = registerDTO.getCode();

        // 1. 邮箱唯一性校验
        if (sysUserMapper.selectByEmail(email) != null) {
            throw new BaseException(MessageConstant.EMAIL_EXISTS);
        }

        // 2. 锁定检查
        if (emailCodeService.isLocked(email)) {
            Long minutes = emailCodeService.getLockRemainingMinutes(email);
            throw new VerifyCodeLockException(
                    MessageConstant.EMAIL_VERIFY_CODE_LOCKED + minutes + " 分钟");
        }

        // 3. 验证码校验
        //    开发模式：配置了 dl.security.dev-code 时直接通过，便于联调
        boolean codeOk = StringUtils.hasText(devCode) && devCode.equals(code.trim())
                || emailCodeService.verifyCode(email, code);
        if (!codeOk) {
            throw new VerifyCodeErrorException(MessageConstant.EMAIL_VERIFY_CODE_ERROR);
        }

        // 4. 后端生成随机用户名（不再使用前端传入的 username）
        String username = usernameGenerator.generate();

        // 5. 创建 sys_user
        //    user_type=1（博客用户），status=1（启用），login_type=1（本地）
        //    password 设置为随机 UUID 的 BCrypt 哈希（占位，邮箱验证码登录不会用到密码字段）
        String randomPassword = UUID.randomUUID().toString();
        String encodedPassword = passwordEncoder.encode(randomPassword);

        SysUser sysUser = SysUser.builder()
                .username(username)
                .password(encodedPassword)
                .nickname(RoleNicknameMapper.getNickname(List.of("GUEST")))
                .email(email)
                .userType(1)
                .status(1)
                .loginType(1)
                .build();

        sysUserMapper.insert(sysUser);

        // 6. 关联 GUEST 角色
        Long guestRoleId = sysUserRoleMapper.selectRoleIdByCode("GUEST");
        if (guestRoleId == null) {
            // 极端情况：sys_role 表未初始化 GUEST 角色，回滚事务
            throw new BaseException(MessageConstant.GUEST_ROLE_NOT_FOUND);
        }

        SysUserRole userRole = SysUserRole.builder()
                .userId(sysUser.getId())
                .roleId(guestRoleId)
                .build();
        sysUserRoleMapper.insert(userRole);

        log.info("博客端用户注册成功: username={}, email={}, userId={}",
                username, email, sysUser.getId());
    }
}
