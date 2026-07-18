package com.xuan.controller.admin;


import com.xuan.dto.ViewPageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IViewService;
import com.xuan.vo.ViewQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端浏览记录接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 * <p>
 * 阶段三改造：移除 BaseContext 依赖，改用 SecurityContextHolder 判断 AUDITOR 角色。
 * </p>
 */
@Slf4j
@RestController("adminViewController")
@RequestMapping("/admin/view")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class ViewController {

    private final IViewService viewService;

    /**
     * 获取浏览记录列表
     * <p>
     * 权限：ADMIN + AUDITOR 均可访问（类级注解已控制）。
     * AUDITOR 角色隐藏 IP 地址（敏感信息只对 ADMIN 可见）。
     * </p>
     * @param viewPageQueryDTO 查询条件
     * @return 浏览记录分页列表
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/page")
    public Result<PageResult> getViewList(ViewPageQueryDTO viewPageQueryDTO) {
        log.info("获取浏览记录列表,{}", viewPageQueryDTO);
        PageResult<ViewQueryVO> pageResult = viewService.pageQuery(viewPageQueryDTO);

        // AUDITOR 角色隐藏 IP 地址（敏感信息只对 ADMIN 可见）
        // 阶段三改造：原 BaseContext.getCurrentRole() 替换为 SecurityContextHolder
        if (hasAuditorRole()) {
            pageResult.getRecords().forEach(v -> v.setIpAddress("审计员账号无法查看"));
        }

        return Result.success(pageResult);
    }

    /**
     * 批量删除浏览记录
     * <p>
     * 权限：仅 ADMIN。AUDITOR 无写权限。
     * </p>
     * @param ids 浏览记录ID列表
     * @return 操作结果
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除浏览记录,{}", ids);
        viewService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 判断当前认证用户是否拥有 AUDITOR 角色
     * <p>
     * 阶段三改造：替代原 {@code BaseContext.getCurrentRole().equals(AdminRoleConstant.VISITOR)}。
     * 直接从 Spring Security 上下文读取角色信息，与"完全移除 BaseContext"目标一致。
     * </p>
     *
     * @return true=当前用户为 AUDITOR 角色
     */
    private boolean hasAuditorRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_AUDITOR".equals(a.getAuthority()));
    }
}
