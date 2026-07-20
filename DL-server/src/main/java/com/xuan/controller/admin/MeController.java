package com.xuan.controller.admin;

import com.xuan.dto.UpdateMeDTO;
import com.xuan.entity.SysUser;
import com.xuan.mapper.SysUserMapper;
import com.xuan.result.Result;
import com.xuan.service.ISysUserService;
import com.xuan.vo.CurrentUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端当前用户信息接口
 * <p>
 * 提供管理端登录用户的基本信息查询(供前端 Admin 端展示当前登录用户、菜单过滤、路由守卫)。
 * </p>
 *
 * <h3>权限</h3>
 * <p>
 * {@code hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')}——所有后台角色均可查看自己的信息。
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
 * 返回的 {@code roles} 列表去除 {@code ROLE_} 前缀,如 {@code ["ADMIN", "AUTHOR"]}。
 * </p>
 *
 * @author xuan
 */
@RestController("adminMeController")
@RequestMapping("/admin/me")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
public class MeController {

    private final SysUserMapper sysUserMapper;
    private final ISysUserService sysUserService;

    /**
     * 获取当前登录用户信息
     * <p>
     * 从 JWT claim {@code user_id} 取 userId,查 sys_user 表组装 VO 返回。
     * </p>
     *
     * @param jwt 由 Resource Server 自动注入的 JWT principal
     * @return 当前用户信息
     */
    @GetMapping
    public Result<CurrentUserVO> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("user_id");
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("管理端 /admin/me 查询用户不存在: userId={}", userId);
            return Result.error("用户不存在");
        }

        // roles claim 形如 ["ROLE_ADMIN", "ROLE_AUTHOR"],去除 ROLE_ 前缀
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

    /**
     * 修改当前登录用户信息
     * <p>
     * 仅允许修改当前登录用户自己的资料。userId 从 JWT claim {@code user_id} 提取,
     * 不接受前端传入,避免越权。
     * </p>
     *
     * <h3>字段规则</h3>
     * <ul>
     *     <li>所有字段可选,只传需要修改的项</li>
     *     <li>修改密码:必须同时提供 {@code oldPassword} 和 {@code newPassword},后端校验旧密码</li>
     *     <li>修改邮箱:校验新邮箱唯一性(sys_user.email 已有 UNIQUE KEY)</li>
     *     <li>修改昵称:直接入库</li>
     * </ul>
     *
     * <h3>前端提示</h3>
     * <p>
     * 修改密码或邮箱后,JWT 中的旧字段不会自动更新,建议前端主动调用
     * {@code POST /api/logout} 登出后重新登录获取新 token。
     * </p>
     *
     * @param jwt 由 Resource Server 自动注入的 JWT principal
     * @param dto 修改参数(字段全部可选,经过 Bean Validation 校验)
     * @return 成功无 data
     */
    @PutMapping
    public Result<Void> updateMe(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody UpdateMeDTO dto) {
        Long userId = jwt.getClaim("user_id");
        sysUserService.updateMe(userId, dto);
        return Result.success();
    }
}
