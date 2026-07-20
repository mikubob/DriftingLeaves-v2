package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.auth.security.SecurityUtil;
import com.xuan.dto.VisitorPageQueryDTO;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IVisitorService;
import com.xuan.vo.VisitorQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端访客接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@Slf4j
@RestController("adminVisitorController")
@RequestMapping("/admin/visitor")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class VisitorController {

    private final IVisitorService visitorService;

    /**
     * 获取访客列表
     * @param visitorPageQueryDTO
     * @return
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/page")
    public Result<PageResult> getVisitorList(VisitorPageQueryDTO visitorPageQueryDTO) {
        log.info("获取访客列表,{}", visitorPageQueryDTO);
        PageResult<VisitorQueryVO> pageResult = visitorService.pageQuery(visitorPageQueryDTO);

        // AUDITOR 角色隐藏 IP 地址（敏感信息只对 ADMIN 可见）
        if (SecurityUtil.hasAuditorRole()) {
            pageResult.getRecords().forEach(v -> v.setIp("审计员账号无法查看"));
        }

        return Result.success(pageResult);
    }

    /**
     * 批量封禁访客
     * @param ids
     * @return
     */
    @PutMapping("/block")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "visitor", targetId = "#ids")
    public Result batchBlock(@RequestParam List<Long> ids) {
        log.info("批量封禁访客: {}", ids);
        visitorService.batchBlock(ids);
        return Result.success();
    }

    /**
     * 批量解封访客
     * @param ids
     * @return
     */
    @PutMapping("/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "visitor", targetId = "#ids")
    public Result<String> batchUnblock(@RequestParam List<Long> ids) {
        log.info("批量解封访客: {}", ids);
        visitorService.batchUnblock(ids);
        return Result.success();
    }

    /**
     * 批量删除访客
     * @param ids
     * @return
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "visitor", targetId = "#ids")
    public Result<String> batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除访客: {}", ids);
        visitorService.batchDeleteVisitors(ids);
        return Result.success();
    }
}
