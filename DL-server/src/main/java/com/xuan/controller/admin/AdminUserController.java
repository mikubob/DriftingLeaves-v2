package com.xuan.controller.admin;

import com.xuan.dto.UserCreateDTO;
import com.xuan.dto.UserPageQueryDTO;
import com.xuan.dto.UserUpdateRolesDTO;
import com.xuan.dto.UserUpdateStatusDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ISysUserService;
import com.xuan.vo.AdminUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理接口
 * <p>
 * 仅 ADMIN 角色可访问，提供用户的增删改查、状态启禁、角色分配能力。
 * </p>
 */
@RestController("adminUserController")
@RequestMapping("/admin/users")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final ISysUserService sysUserService;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public Result<PageResult<AdminUserVO>> pageUsers(@Valid UserPageQueryDTO dto) {
        return Result.success(sysUserService.pageUsers(dto));
    }

    /**
     * 新增用户
     * <p>
     * 仅支持邮箱创建，默认禁用状态，初始密码由管理员传入。
     * </p>
     */
    @PostMapping
    public Result<Long> createUser(@Valid @RequestBody UserCreateDTO dto,
                                   @AuthenticationPrincipal Jwt jwt) {
        Long operatorId = jwt.getClaim("user_id");
        Long userId = sysUserService.createUser(dto, operatorId);
        return Result.success(userId);
    }

    /**
     * 修改用户状态（启用/禁用）
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long userId,
                                     @Valid @RequestBody UserUpdateStatusDTO dto,
                                     @AuthenticationPrincipal Jwt jwt) {
        Long operatorId = jwt.getClaim("user_id");
        sysUserService.updateUserStatus(userId, dto.getStatus(), operatorId);
        return Result.success();
    }

    /**
     * 修改用户角色
     * <p>
     * 只能分配非 ADMIN 角色。
     * </p>
     */
    @PutMapping("/{id}/roles")
    public Result<Void> updateRoles(@PathVariable("id") Long userId,
                                    @Valid @RequestBody UserUpdateRolesDTO dto,
                                    @AuthenticationPrincipal Jwt jwt) {
        Long operatorId = jwt.getClaim("user_id");
        sysUserService.updateUserRoles(userId, dto, operatorId);
        return Result.success();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable("id") Long userId,
                                   @AuthenticationPrincipal Jwt jwt) {
        Long operatorId = jwt.getClaim("user_id");
        sysUserService.deleteUser(userId, operatorId);
        return Result.success();
    }
}
