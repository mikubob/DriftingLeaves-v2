package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.dto.RegisterDTO;
import com.xuan.dto.SendEmailCodeDTO;
import com.xuan.entity.SysUser;
import com.xuan.mapper.SysUserMapper;
import com.xuan.result.Result;
import com.xuan.service.BlogUserService;
import com.xuan.vo.CurrentUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端认证接口(注册 + 发送邮箱验证码 + 当前用户信息)
 * <p>
 * 登录走 OAuth2 端点 {@code POST /oauth2/token}(grant_type=email_code),
 * 不在本 Controller 处理,由 {@link com.xuan.auth.security.EmailCodeAuthenticationProvider} 负责。
 * </p>
 *
 * <h3>接口清单</h3>
 * <ul>
 *     <li>{@code POST /blog/auth/sendCode}:发送邮箱验证码(permitAll)</li>
 *     <li>{@code POST /blog/auth/register}:用户注册(permitAll)</li>
 *     <li>{@code GET  /blog/auth/me}:获取当前登录用户信息(hasRole('GUEST'))</li>
 * </ul>
 *
 * <h3>/blog/auth/me 权限特例</h3>
 * <p>
 * {@code /blog/auth/**} 整体 permitAll,但 {@code /blog/auth/me} 需要 GUEST 角色。
 * {@code ResourceServerConfig} 中已将 {@code GET /blog/auth/me} 特例放在
 * {@code /blog/auth/**} permitAll 之前,确保特例优先匹配。
 * </p>
 */
@RestController("blogAuthController")
@RequestMapping("/blog/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final BlogUserService blogUserService;
    private final SysUserMapper sysUserMapper;

    /**
     * 发送邮箱验证码
     * <p>
     * 注册与登录共用:验证码按 email 维度存储在 Redis,5 分钟有效,60s 频率限制。
     * </p>
     */
    @PostMapping("/sendCode")
    @RateLimit(type = RateLimit.Type.IP, tokens = 3, burstCapacity = 5,
            timeWindow = 60, message = "请求过于频繁,请稍后再试")
    public Result<String> sendCode(@Valid @RequestBody SendEmailCodeDTO sendEmailCodeDTO) {
        log.info("博客端发送邮箱验证码: email={}", sendEmailCodeDTO.getEmail());
        blogUserService.sendEmailCode(sendEmailCodeDTO.getEmail());
        return Result.success();
    }

    /**
     * 用户注册
     * <p>
     * 用户名 + 邮箱 + 邮箱验证码三要素注册,注册成功后自动关联 GUEST 角色,
     * 用户即可使用邮箱验证码登录。
     * </p>
     */
    @PostMapping("/register")
    @RateLimit(type = RateLimit.Type.IP, tokens = 3, burstCapacity = 5,
            timeWindow = 60, message = "请求过于频繁,请稍后再试")
    public Result<String> register(@Valid @RequestBody RegisterDTO registerDTO) {
        log.info("博客端用户注册: username={}, email={}",
                registerDTO.getUsername(), registerDTO.getEmail());
        blogUserService.register(registerDTO);
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     * <p>
     * 供前端 Blog 端展示当前登录用户、路由守卫使用。
     * </p>
     *
     * <h3>principal 类型说明</h3>
     * <p>
     * 资源服务器 JWT 解码后,principal 是 {@link Jwt} 对象(非 SecurityUser)。
     * 通过 {@code jwt.getClaim("user_id")} 获取用户 ID,再查库获取最新用户信息。
     * </p>
     *
     * <h3>角色格式约定(决策 9)</h3>
     * <p>
     * 返回的 {@code roles} 列表去除 {@code ROLE_} 前缀。
     * 博客端注册用户默认且仅有 GUEST 角色,因此通常返回 {@code ["GUEST"]}。
     * </p>
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('GUEST')")
    public Result<CurrentUserVO> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("user_id");
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("博客端 /blog/auth/me 查询用户不存在: userId={}", userId);
            return Result.error("用户不存在");
        }

        // roles claim 形如 ["ROLE_GUEST"],去除 ROLE_ 前缀
        List<String> rawRoles = jwt.getClaim("roles");
        List<String> roles = rawRoles == null ? List.of()
                : rawRoles.stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .toList();

        CurrentUserVO vo = CurrentUserVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .roles(roles)
                .build();

        return Result.success(vo);
    }
}
