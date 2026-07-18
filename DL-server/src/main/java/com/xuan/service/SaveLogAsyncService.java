package com.xuan.service;

import com.xuan.annotation.OperationLog;
import org.aspectj.lang.JoinPoint;

/**
 * 异步保存操作日志服务接口
 * <p>
 * 阶段三改造：方法签名新增 {@code userId} 参数。
 * </p>
 *
 * <h3>为何不直接在异步方法内取 SecurityContextHolder？</h3>
 * <p>
 * {@code @Async} 方法会在独立的线程池线程中执行，而 Spring Security 的
 * {@code SecurityContextHolder} 默认使用 {@code ThreadLocal} 策略，
 * 上下文不会自动传递到异步线程。因此必须在调用异步方法前（请求线程内）
 * 提取 userId，然后作为方法参数传入。
 * </p>
 *
 * <h3>调用方约定</h3>
 * <p>
 * {@link com.xuan.aspect.OperationLogAspect} 在 {@code finally} 块中调用本方法，
 * finally 块仍在请求线程内执行，此时 SecurityContextHolder 仍持有当前用户身份，
 * 可安全地提取 userId 并传入。
 * </p>
 */
public interface SaveLogAsyncService {

    /**
     * 异步保存日志
     *
     * @param joinPoint    切点，包含被拦截方法的信息
     * @param result       方法执行结果（暂未使用）
     * @param error        方法执行异常，不为 null 表示操作失败
     * @param operationLog 操作日志注解，包含操作类型、目标等信息
     * @param userId       当前操作用户 ID（由调用方在请求线程内从 SecurityContextHolder 提取，
     *                     避免异步线程无法访问 ThreadLocal 上下文）
     */
    void saveLogAsync(JoinPoint joinPoint, Object result, Throwable error,
                      OperationLog operationLog, Long userId);
}
