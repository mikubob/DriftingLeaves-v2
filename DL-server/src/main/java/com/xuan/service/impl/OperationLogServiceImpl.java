package com.xuan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.dto.OperationLogPageQueryDTO;
import com.xuan.entity.OperationLogs;
import com.xuan.mapper.OperationLogMapper;
import com.xuan.result.PageResult;
import com.xuan.service.IOperationLogService;
import com.xuan.vo.OperationLogQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 操作日志服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLogs> implements IOperationLogService {

    /**
     * 保存操作日志
     * @param operationLogs 操作日志
     */
    @Override
    public void saveLog(OperationLogs operationLogs) {
        save(operationLogs);
    }

    /**
     * 分页查询操作日志
     * @param operationLogPageQueryDTO 分页查询参数
     * @return 分页查询结果
     */
    @Override
    public PageResult pageQuery(OperationLogPageQueryDTO operationLogPageQueryDTO) {
        //1.创建分页对象
        Page<OperationLogs> page=new Page<>(operationLogPageQueryDTO.getPage(),operationLogPageQueryDTO.getPageSize());
        //2.创建查询条件
        Page<OperationLogs> opPage = page(page, builderQueryWrapper(operationLogPageQueryDTO));
        //3.转换为 QueryVO 并返回
        Page<OperationLogQueryVO> voPage = new Page<>(opPage.getCurrent(), opPage.getSize(), opPage.getTotal());
        voPage.setRecords(opPage.getRecords().stream()
                .map(log -> cn.hutool.core.bean.BeanUtil.copyProperties(log, OperationLogQueryVO.class))
                .toList());
        return PageResult.fromIPage(voPage);
    }

    /**
     * 批量删除操作日志
     * @param ids 要删除的操作日志 ID 列表
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        removeBatchByIds(ids);
    }

    //<========私有辅助方法==========>

    /**
     * 构建查询条件
     * @param operationLogPageQueryDTO 查询参数
     * @return 查询条件对象
     */
    private LambdaQueryWrapper<OperationLogs> builderQueryWrapper(OperationLogPageQueryDTO operationLogPageQueryDTO) {
        //1.构建查询条件对象
        LambdaQueryWrapper<OperationLogs> queryWrapper = new LambdaQueryWrapper<>();
        //2.根据查询参数添加查询条件
        if (operationLogPageQueryDTO.getUserId() != null) {
            queryWrapper.eq(OperationLogs::getUserId, operationLogPageQueryDTO.getUserId());
        }
        if (operationLogPageQueryDTO.getOperationType() != null && !operationLogPageQueryDTO.getOperationType().isEmpty()) {
            queryWrapper.eq(OperationLogs::getOperationType, operationLogPageQueryDTO.getOperationType());
        }
        if (operationLogPageQueryDTO.getOperationTarget() != null && !operationLogPageQueryDTO.getOperationTarget().isEmpty()) {
            queryWrapper.like(OperationLogs::getOperationTarget, operationLogPageQueryDTO.getOperationTarget());
        }
        if (operationLogPageQueryDTO.getStartTime() != null) {
            queryWrapper.ge(OperationLogs::getOperationTime, operationLogPageQueryDTO.getStartTime());
        }
        if (operationLogPageQueryDTO.getEndTime() != null) {
            queryWrapper.le(OperationLogs::getOperationTime, operationLogPageQueryDTO.getEndTime());
        }
        queryWrapper.orderByDesc(OperationLogs::getOperationTime);
        //3.返回查询条件对象
        return queryWrapper;
    }
}
