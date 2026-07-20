package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.RegisterDTO;
import com.xuan.dto.SendEmailCodeDTO;
import com.xuan.result.Result;
import com.xuan.service.BlogUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端认证接口（注册 + 发送邮箱验证码）
 * <p>
 * 登录走 OAuth2 端点 {@code POST /oauth2/token}（grant_type=email_code），
 * 不在本 Controller 处理，由 {@link com.xuan.auth.security.EmailCodeAuthenticationProvider} 负责。
 * </p>
 */
@RestController("blogAuthController")
@RequestMapping("/blog/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final BlogUserService blogUserService;

    /**
     * 发送邮箱验证码
     * <p>
     * 注册与登录共用：验证码按 email 维度存储在 Redis，5 分钟有效，60s 频率限制。
     * </p>
     */
    @PostMapping("/sendCode")
    @RateLimit(type = RateLimit.Type.IP, tokens = 3, burstCapacity = 5,
            timeWindow = 60, message = "请求过于频繁，请稍后再试")
    public Result<String> sendCode(@Valid @RequestBody SendEmailCodeDTO sendEmailCodeDTO) {
        log.info("博客端发送邮箱验证码: email={}", sendEmailCodeDTO.getEmail());
        blogUserService.sendEmailCode(sendEmailCodeDTO.getEmail());
        return Result.success();
    }

    /**
     * 用户注册
     * <p>
     * 用户名 + 邮箱 + 邮箱验证码三要素注册，注册成功后自动关联 GUEST 角色，
     * 用户即可使用邮箱验证码登录。
     * </p>
     */
    @PostMapping("/register")
    @RateLimit(type = RateLimit.Type.IP, tokens = 3, burstCapacity = 5,
            timeWindow = 60, message = "请求过于频繁，请稍后再试")
    public Result<String> register(@Valid @RequestBody RegisterDTO registerDTO) {
        log.info("博客端用户注册: username={}, email={}",
                registerDTO.getUsername(), registerDTO.getEmail());
        blogUserService.register(registerDTO);
        return Result.success();
    }
}
