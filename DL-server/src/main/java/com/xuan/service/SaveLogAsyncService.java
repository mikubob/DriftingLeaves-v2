package com.xuan.service;

import com.xuan.annotation.OperationLog;
import org.aspectj.lang.JoinPoint;

public interface SaveLogAsyncService {

    /**
     * 异步保存日志
     * @param joinPoint
     * @param result
     * @param error
     * @param operationLog
     */
    void saveLogAsync(JoinPoint joinPoint, Object result, Throwable error,
                      OperationLog operationLog);
}
