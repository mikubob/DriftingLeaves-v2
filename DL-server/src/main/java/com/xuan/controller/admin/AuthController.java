package com.xuan.controller.admin;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.SendEmailCodeDTO;
import com.xuan.entity.SysUser;
import com.xuan.exception.VerifyCodeCoolDownException;
import com.xuan.exception.VerifyCodeLockException;
import com.xuan.mapper.SysUserMapper;
import com.xuan.result.Result;
import com.xuan.service.AsyncEmailService;
import com.xuan.service.VerifyCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证接口
 * <p>
 * 管理端登录走自定义 grant_type {@code admin_password_code}(用户名+密码+验证码),
 * 登录本身由 {@link com.xuan.auth.security.AdminPasswordCodeAuthenticationProvider} 在
 * SAS Token Endpoint 处理。本 Controller 仅提供登录前置接口——发送邮箱验证码。
 * </p>
 *
 * <h3>验证码存储维度</h3>
 * <p>
 * 管理端验证码按 {@code userId} 维度存储(复用 {@link VerifyCodeService}),
 * 与 {@link com.xuan.auth.security.AdminPasswordCodeAuthenticationProvider} 的校验逻辑完全对齐。
 * 博客端验证码则按 {@code email} 维度存储(走 {@link com.xuan.service.EmailCodeService}),
 * 两套服务正交,互不干扰。
 * </p>
 *
 * <h3>安全策略</h3>
 * <ul>
 *     <li>频率限制:60s 内同一 userId 仅允许发一次(由 VerifyCodeService 控制)</li>
 *     <li>锁定策略:验证码连续错误 5 次锁定 30 分钟</li>
 *     <li>账号嗅探防护:无论邮箱是否对应真实用户,接口均返回 success(决策 1)</li>
 *     <li>IP 限流:同一 IP 60s 内最多 3 次请求(令牌桶)</li>
 * </ul>
 *
 * @author xuan
 */
@RestController("adminAuthController")
@RequestMapping("/admin/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final VerifyCodeService verifyCodeService;
    private final AsyncEmailService asyncEmailService;

    /**
     * 发送管理端登录验证码
     * <p>
     * 通过邮箱找到对应 sys_user,向该邮箱发送验证码,并以 userId 为 key 写入 VerifyCodeService。
     * </p>
     *
     * <h3>安全说明(决策 1)</h3>
     * <p>
     * 为防止账号嗅探,无论邮箱是否对应真实用户,接口均返回 success。
     * 邮箱不存在时仅记日志,不抛异常。前端登录时若验证码错误,统一提示
     * "验证码错误或已过期"即可。
     * </p>
     *
     * @param dto 邮箱参数
     * @return Result.success()
     */
    @PostMapping("/sendCode")
    @RateLimit(type = RateLimit.Type.IP, tokens = 3, burstCapacity = 5,
            timeWindow = 60, message = "请求过于频繁,请稍后再试")
    public Result<Void> sendCode(@Valid @RequestBody SendEmailCodeDTO dto) {
        log.info("管理端发送登录验证码: email={}", dto.getEmail());

        SysUser user = sysUserMapper.selectByEmail(dto.getEmail());
        if (user == null) {
            // 决策 1:防账号嗅探,邮箱不存在时静默返回 success,仅记日志
            log.warn("管理端发送验证码:邮箱未对应任何用户,静默返回 success: email={}", dto.getEmail());
            return Result.success();
        }

        // 锁定检查:用户因验证码错误次数过多被锁定时,不允许再发送新验证码
        if (verifyCodeService.isLocked(user.getId())) {
            Long minutes = verifyCodeService.getLockRemainingMinutes(user.getId());
            throw new VerifyCodeLockException("验证码尝试次数过多,请 " + minutes + " 分钟后再试");
        }

        // 频率限制:60s 内仅允许发一次
        if (!verifyCodeService.canSendCode(user.getId())) {
            Long seconds = verifyCodeService.getRemainingCooldown(user.getId());
            throw new VerifyCodeCoolDownException("验证码发送过于频繁,请 " + seconds + " 秒后再试");
        }

        // 生成并保存验证码(按 userId 维度,与 AdminPasswordCodeAuthenticationProvider 校验逻辑对齐)
        String code = verifyCodeService.generateCode();
        verifyCodeService.saveCode(user.getId(), code);

        // 异步发送邮件
        asyncEmailService.sendVerifyCodeAsync(user.getEmail(), code);

        log.info("管理端验证码发送成功: userId={}, email={}", user.getId(), user.getEmail());
        return Result.success();
    }
}
