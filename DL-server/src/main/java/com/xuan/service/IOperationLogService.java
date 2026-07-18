package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.OperationLogPageQueryDTO;
import com.xuan.entity.OperationLogs;
import com.xuan.result.PageResult;

import java.util.List;

public interface IOperationLogService extends IService<OperationLogs> {
    /**
     * 保存操作日志
     * @param operationLogs
     */
    void saveLog(OperationLogs operationLogs);

    /**
     * 分页查询操作日志
     * @param operationLogPageQueryDTO
     * @return
     */
    PageResult pageQuery(OperationLogPageQueryDTO operationLogPageQueryDTO);

    /**
     * 批量删除操作日志
     * @param ids
     */
    void batchDelete(List<Long> ids);
}
