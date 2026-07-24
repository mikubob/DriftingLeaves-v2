package com.xuan.controller.admin;

import com.xuan.dto.ApplyUsernameDTO;
import com.xuan.dto.UpdateMeDTO;
import com.xuan.entity.SysUser;
import com.xuan.entity.SysUserProfileAudit;
import com.xuan.mapper.SysUserMapper;
import com.xuan.mapper.SysUserProfileAuditMapper;
import com.xuan.result.Result;
import com.xuan.service.CommonService;
import com.xuan.service.ISysUserService;
import com.xuan.utils.IpUtil;
import com.xuan.vo.CurrentUserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理端当前用户信息接口
 * <p>
 * 提供管理端登录用户的基本信息查询(供前端 Admin 端展示当前登录用户、菜单过滤、路由守卫),
 * 以及当前登录用户修改用户名、头像、密码、邮箱等操作。
 * </p>
 *
 * <h3>权限</h3>
 * <p>
 * {@code hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')}——所有后台角色均可查看和修改自己的账号信息。
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
    private final CommonService commonService;
    private final SysUserProfileAuditMapper auditMapper;

    private static final int AUDIT_TYPE_USERNAME = 1;
    private static final int AUDIT_TYPE_AVATAR = 2;

    /**
     * 获取当前登录用户信息
     * <p>
     * 从 JWT claim {@code user_id} 取 userId,查 sys_user 表组装 VO 返回。
     * 同时返回待审核的用户名/头像以及密码最后修改时间,供前端展示冷却状态。
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

        SysUserProfileAudit pendingUsername = auditMapper.selectPendingByUserAndType(userId, AUDIT_TYPE_USERNAME);
        SysUserProfileAudit pendingAvatar = auditMapper.selectPendingByUserAndType(userId, AUDIT_TYPE_AVATAR);

        CurrentUserVO vo = CurrentUserVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .roles(roles)
                .pendingUsername(pendingUsername != null ? pendingUsername.getNewValue() : null)
                .pendingAvatar(pendingAvatar != null ? pendingAvatar.getNewValue() : null)
                .passwordModifyTime(user.getPasswordModifyTime())
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
     *     <li>修改密码:必须同时提供 {@code oldPassword} 和 {@code newPassword},后端校验旧密码,且 15 天内只能修改一次</li>
     *     <li>修改邮箱:校验新邮箱唯一性(sys_user.email 已有 UNIQUE KEY)</li>
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

    /**
     * 申请修改当前登录用户名
     * <p>
     * 提交后进入待审核状态,需管理员审核通过后才更新 sys_user.username。
     * 同一账号 15 天内只能申请一次。
     * </p>
     */
    @PostMapping("/username")
    public Result<String> applyUsername(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ApplyUsernameDTO dto,
                                        HttpServletRequest request) {
        Long userId = jwt.getClaim("user_id");
        String clientIp = IpUtil.getClientIp(request);
        sysUserService.applyUsernameChange(userId, dto.getUsername(), clientIp);
        return Result.success("用户名修改申请已提交，审核通过前仍显示旧用户名");
    }

    /**
     * 申请修改当前登录用户头像
     * <p>
     * 提交后进入待审核状态,需管理员审核通过后才更新 sys_user.avatar。
     * 同一账号 30 天内、同一 IP 下只能申请一次。
     * </p>
     */
    @PostMapping("/avatar")
    public Result<String> applyAvatar(@AuthenticationPrincipal Jwt jwt,
                                      MultipartFile file,
                                      HttpServletRequest request) {
        Long userId = jwt.getClaim("user_id");
        if (file == null || file.isEmpty()) {
            return Result.error("请上传头像文件");
        }
        String avatarUrl = commonService.uploadFile(file);
        String clientIp = IpUtil.getClientIp(request);
        sysUserService.applyAvatarChange(userId, avatarUrl, clientIp);
        return Result.success("头像修改申请已提交，审核通过前仍显示旧头像");
    }
}
