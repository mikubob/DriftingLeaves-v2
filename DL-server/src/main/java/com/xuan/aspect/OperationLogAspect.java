package com.xuan.aspect;


import com.xuan.annotation.OperationLog;
import com.xuan.auth.security.SecurityUser;
import com.xuan.service.SaveLogAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 操作日志切面
 * <p>
 * 拦截所有标注 {@link OperationLog} 注解的方法，将操作信息异步保存到数据库。
 * </p>
 *
 * <h3>阶段三改造说明</h3>
 * <p>
 * 移除了对 {@code BaseContext} 的间接依赖。原方案由 {@code SaveLogAsyncServiceImpl}
 * 在 @Async 方法内部通过 {@code BaseContext.getCurrentId()} 获取用户 ID；
 * 阶段三完全移除 BaseContext 后，改为在本切面（请求线程内）从
 * {@link SecurityContextHolder} 提取 {@link SecurityUser#getUserId()}，
 * 然后作为参数传递给 {@link SaveLogAsyncService#saveLogAsync}。
 * </p>
 *
 * <h3>为何要在切面提取 userId？</h3>
 * <ul>
 *     <li>切面的 {@code finally} 块仍在请求线程内执行，{@link SecurityContextHolder} 仍持有上下文</li>
 *     <li>{@code @Async} 异步方法运行在线程池线程中，无法访问请求线程的 ThreadLocal</li>
 *     <li>显式传参是最简洁、最可靠的跨线程传递方式</li>
 * </ul>
 *
 * @author xuan
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {

    /**
     * 异步日志保存服务
     */
    private final SaveLogAsyncService saveLogAsyncService;

    /**
     * 定义切入点：所有标注 {@link OperationLog} 注解的方法
     */
    @Pointcut("@annotation(com.xuan.annotation.OperationLog)")
    public void operationLogPointCut() {
    }

    /**
     * 环绕通知：在目标方法执行前后记录操作日志
     * <p>
     * 执行流程：
     * </p>
     * <ol>
     *     <li>执行目标方法，捕获结果或异常</li>
     *     <li>在 finally 块中（仍处于请求线程）提取当前用户 ID</li>
     *     <li>调用异步服务保存日志，将 userId 作为参数传入</li>
     * </ol>
     *
     * @param joinPoint 切点
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常（原样抛出，不吞异常）
     */
    @Around("operationLogPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = null;
        Throwable error = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e; // 重新抛出异常
        } finally {
            // 获取方法上的 @OperationLog 注解
            MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 方法签名对象
            Method method = signature.getMethod(); // 方法对象
            OperationLog operationLog = method.getAnnotation(OperationLog.class); // 获取方法上的注解对象

            if (operationLog != null) {
                // 在请求线程内提取当前用户 ID（@Async 线程无法访问 SecurityContextHolder）
                Long userId = getCurrentUserId();

                // 异步记录操作日志（userId 作为参数传入，避免跨线程 ThreadLocal 失效）
                saveLogAsyncService.saveLogAsync(joinPoint, result, error, operationLog, userId);
            }
        }
    }

    /**
     * 从 Spring Security 上下文提取当前登录用户 ID
     * <p>
     * 兼容多种 principal 类型：
     * </p>
     * <ul>
     *     <li>{@link Jwt}：Resource Server 验证后的标准情况，从 {@code user_id} claim 读取
     *         （由 {@link com.xuan.auth.config.JwtCustomizerConfig} 在颁发 Token 时注入）</li>
     *     <li>{@link SecurityUser}：极少数情况（如 SAS 内部流程），直接调用 {@code getUserId()}</li>
     *     <li>其他类型：返回 null（未登录或匿名访问，如登录接口本身）</li>
     * </ul>
     *
     * <h3>JWT claim 读取说明</h3>
     * <p>
     * {@code user_id} claim 由 {@link com.xuan.auth.config.JwtCustomizerConfig} 在颁发 Token 时写入，
     * 类型为 Long。但 JWT 经过 JSON 序列化/反序列化后，claim 值可能变为 Integer 或 Long，
     * 因此使用 {@code Number.longValue()} 统一转换，避免类型转换异常。
     * </p>
     *
     * @return 当前用户 ID；未登录或无法识别时返回 null
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // 标准情况：Resource Server 解析 JWT 后，principal 是 Jwt 对象
        // 从 user_id claim 提取用户 ID（由 JwtCustomizerConfig 在颁发 Token 时注入）
        if (principal instanceof Jwt jwt) {
            Object userIdClaim = jwt.getClaim("user_id");
            if (userIdClaim instanceof Number number) {
                return number.longValue();
            }
            return null;
        }

        // 兼容情况：principal 直接是 SecurityUser（如 SAS 内部流程或测试场景）
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }

        // 未识别的 principal 类型（如 String），返回 null，日志的 user_id 字段将为 null
        // 这种情况主要出现在登录接口本身（认证前）或匿名访问
        return null;
    }
}
