package com.xuan.controller.admin;


import com.xuan.dto.OperationLogPageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IOperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端操作日志接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@Slf4j
@RestController("adminOperationLogController")
@RequestMapping("/admin/operationLog")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class OperationLogController {

    private final IOperationLogService operationLogService;

    /**
     * 分页查询操作日志
     * @param operationLogPageQueryDTO 分页查询参数
     * @return 分页查询结果
     */
    @GetMapping("/page")
    public Result<PageResult> pageQuery(OperationLogPageQueryDTO operationLogPageQueryDTO) {
        log.info("分页查询操作日志,{}", operationLogPageQueryDTO);
        PageResult pageResult = operationLogService.pageQuery(operationLogPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除操作日志
     * @param ids 要删除的操作日志 ID 列表
     * @return 删除结果
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除操作日志,{}", ids);
        operationLogService.batchDelete(ids);
        return Result.success();
    }
}
