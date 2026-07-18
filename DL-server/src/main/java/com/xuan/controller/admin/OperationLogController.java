package com.xuan.controller.admin;


import com.xuan.dto.OperationLogPageQueryDTO;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IOperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端操作日志接口
 */
@Slf4j
@RestController("adminOperationLogController")
@RequestMapping("/admin/operationLog")
@RequiredArgsConstructor
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
    public Result batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除操作日志,{}", ids);
        operationLogService.batchDelete(ids);
        return Result.success();
    }
}
