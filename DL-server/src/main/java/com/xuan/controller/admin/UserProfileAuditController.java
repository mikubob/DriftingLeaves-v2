package com.xuan.controller.admin;

import com.xuan.dto.ProfileAuditDTO;
import com.xuan.result.Result;
import com.xuan.service.ISysUserService;
import com.xuan.vo.ProfileAuditVO;
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
 * 管理端用户名/头像修改审核接口
 */
@RestController("adminUserProfileAuditController")
@RequestMapping("/admin/user/profile-audit")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserProfileAuditController {

    private final ISysUserService sysUserService;

    /**
     * 查询所有待审核的用户名/头像修改申请
     */
    @GetMapping("/pending")
    public Result<List<ProfileAuditVO>> listPending() {
        return Result.success(sysUserService.listPendingProfileAudits());
    }

    /**
     * 审核用户名/头像修改申请
     */
    @PostMapping("/audit")
    public Result<String> audit(@Valid @RequestBody ProfileAuditDTO dto,
                                @AuthenticationPrincipal Jwt jwt) {
        Long auditorId = jwt.getClaim("user_id");
        sysUserService.auditProfileChange(dto, auditorId);
        return dto.getStatus() == 1
                ? Result.success("审核通过")
                : Result.success("已拒绝");
    }
}
