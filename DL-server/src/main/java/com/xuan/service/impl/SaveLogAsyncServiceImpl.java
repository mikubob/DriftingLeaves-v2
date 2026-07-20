package com.xuan.service.impl;


import com.alibaba.fastjson2.JSON;
import com.xuan.annotation.OperationLog;
import com.xuan.constant.StatusConstant;
import com.xuan.entity.OperationLogs;
import com.xuan.service.IOperationLogService;
import com.xuan.service.SaveLogAsyncService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步保存操作日志服务实现类
 * <p>
 * 该类负责异步保存操作日志，支持以下功能：
 * <ul>
 *     <li>通过 SpEL 表达式解析目标 ID</li>
 *     <li>自动过滤敏感参数（如密码、令牌等）</li>
 *     <li>限制日志数据长度，防止过大的日志数据</li>
 *     <li>使用缓存优化参数名解析性能</li>
 * </ul>
 * </p>
 *
 * @author xuan
 * @see SaveLogAsyncService
 * @see OperationLog
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SaveLogAsyncServiceImpl implements SaveLogAsyncService {

    /**
     * 操作日志服务
     */
    private final IOperationLogService operationLogService;

    /**
     * SpEL 表达式解析器，用于解析目标 ID 表达式
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 参数名发现器，用于获取方法参数名称
     */
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    /**
     * 参数名缓存，避免重复解析同一方法的参数名
     * <p>使用 ConcurrentHashMap 保证线程安全</p>
     */
    private final Map<Method, String[]> parameterNamesCache = new ConcurrentHashMap<>();

    /**
     * 错误信息最大长度
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    /**
     * 操作数据最大长度
     */
    private static final int MAX_OPERATE_DATA_LENGTH = 5000;

    /**
     * 敏感值掩码，用于替换敏感参数值
     */
    private static final String SENSITIVE_VALUE_MASK = "***";

    /**
     * 敏感字段关键词列表
     * <p>包含这些关键词的字段将被视为敏感字段，其值会被掩码替换</p>
     */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "password", "pwd", "token", "salt", "secret", "key", "credential"
    );

    /**
     * 异步保存操作日志
     * <p>
     * 该方法使用 @Async 注解实现异步执行，不会阻塞主线程。
     * 会记录操作类型、操作目标、操作时间、操作结果、操作用户、目标 ID 和操作数据等信息。
     * </p>
     *
     * @param joinPoint    切点，包含被拦截方法的信息
     * @param error        方法执行异常，不为 null 表示操作失败
     * @param operationLog 操作日志注解，包含操作类型、目标等信息
     * @param userId       当前操作用户 ID（由调用方在请求线程内提取并传入，避免 @Async 线程无法访问 ThreadLocal 上下文）
     */
    @Async("taskExecutor")
    @Override
    public void saveLogAsync(JoinPoint joinPoint,
                             Throwable error, OperationLog operationLog, Long userId) {
        OperationLogs operationLogs = new OperationLogs();

        try {
            // 设置基本信息
            operationLogs.setOperationType(operationLog.value().toString());
            operationLogs.setOperationTarget(operationLog.target());
            operationLogs.setOperationTime(LocalDateTime.now());

            // 记录操作结果：成功或失败
            if (error != null) {
                operationLogs.setResult(StatusConstant.DISABLE);
                operationLogs.setErrorMessage(getErrorMessage(error));
            } else {
                operationLogs.setResult(StatusConstant.ENABLE);
            }

            // 记录操作用户 ID（由调用方在请求线程内从 SecurityContextHolder 提取并传入）
            if (userId != null) {
                operationLogs.setUserId(userId);
            }

            // 解析并设置目标 ID（通过 SpEL 表达式）
            if (!operationLog.targetId().isEmpty()) {
                Long targetId = parseTargetId(joinPoint, operationLog.targetId());
                if (targetId != null) {
                    operationLogs.setTargetId(targetId);
                }
            }

            // 记录操作数据（可选）
            if (operationLog.saveData()) {
                String operateData = buildOperateData(joinPoint);
                // 限制操作数据长度，防止日志过大
                if (operateData != null && operateData.length() > MAX_OPERATE_DATA_LENGTH) {
                    operateData = operateData.substring(0, MAX_OPERATE_DATA_LENGTH) + "...";
                }
                operationLogs.setOperateData(operateData);
            }

            // 保存日志到数据库
            operationLogService.saveLog(operationLogs);
        } catch (Exception e) {
            log.error("保存操作日志失败, 操作类型: {}, 操作目标: {}",
                    operationLog.value(), operationLog.target(), e);
        }
    }

    /**
     * 获取错误信息
     * <p>
     * 格式为：异常类名: 异常消息
     * 会限制错误信息长度，防止过长的错误消息
     * </p>
     *
     * @param error 异常对象
     * @return 格式化后的错误信息，如果 error 为 null 则返回 null
     */
    private String getErrorMessage(Throwable error) {
        if (error == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(error.getClass().getSimpleName())
                .append(": ")
                .append(error.getMessage());

        String message = sb.toString();
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }

        return message;
    }

    /**
     * 解析目标 ID（通过 SpEL 表达式）
     * <p>
     * 支持以下表达式格式：
     * <ul>
     *     <li>#id - 引用名为 id 的参数</li>
     *     <li>#p0 - 引用第一个参数</li>
     *     <li>#user.id - 引用 user 参数的 id 属性</li>
     *     <li>#ids - 引用集合参数，会取第一个元素</li>
     * </ul>
     * </p>
     *
     * @param joinPoint           切点
     * @param targetIdExpression  SpEL 表达式
     * @return 解析后的目标 ID，解析失败返回 null
     */
    private Long parseTargetId(JoinPoint joinPoint, String targetIdExpression) {
        try {
            if (targetIdExpression == null || targetIdExpression.isEmpty()) {
                return null;
            }

            // 创建 SpEL 上下文
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 获取方法参数
            Object[] args = joinPoint.getArgs();
            if (args == null) {
                args = new Object[0];
            }

            // 获取方法参数名并设置到上下文
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            String[] paramNames = getParameterNames(method);

            // 按参数名设置变量（如 #id）
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            // 按参数索引设置变量（如 #p0, #p1）
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
            }

            // 解析 SpEL 表达式
            Expression expression = parser.parseExpression(targetIdExpression);
            Object value = expression.getValue(context);

            // 处理集合类型（批量操作时取第一个元素）
            if (value instanceof Collection<?>) {
                Collection<?> col = (Collection<?>) value;
                if (col.isEmpty()) return null;
                value = col.iterator().next();
            }

            // 转换为长整数
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value != null) {
                try {
                    return Long.parseLong(value.toString());
                } catch (NumberFormatException e) {
                    log.warn("目标ID无法转换为长整数: {}", value);
                    return null;
                }
            }

            return null;

        } catch (Exception e) {
            log.warn("解析目标ID表达式失败: {}", targetIdExpression, e);
            return null;
        }
    }

    /**
     * 构建操作数据
     * <p>
     * 将方法参数转换为 JSON 字符串，用于记录操作详情。
     * 会自动过滤以下类型的参数：
     * <ul>
     *     <li>ServletRequest - 请求对象</li>
     *     <li>ServletResponse - 响应对象</li>
     *     <li>MultipartFile - 上传文件</li>
     * </ul>
     * 同时会过滤敏感参数值。
     * </p>
     *
     * @param joinPoint 切点
     * @return JSON 格式的操作数据，无参数时返回 null
     */
    private String buildOperateData(JoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return null;
            }

            Map<String, Object> params = new HashMap<>();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = getParameterNames(signature.getMethod());

            for (int i = 0; i < args.length; i++) {
                String paramName = (paramNames != null && i < paramNames.length)
                        ? paramNames[i] : "arg" + i;

                // 跳过不可序列化的 Servlet/IO 类型参数
                if (args[i] instanceof ServletRequest
                        || args[i] instanceof ServletResponse
                        || args[i] instanceof MultipartFile) {
                    continue;
                }

                // 过滤敏感参数
                Object paramValue = filterSensitiveParam(paramName, args[i]);
                params.put(paramName, paramValue);
            }

            return JSON.toJSONString(params);

        } catch (Exception e) {
            log.warn("构建操作数据失败", e);
            return null;
        }
    }

    /**
     * 过滤敏感参数
     * <p>
     * 如果参数名包含敏感关键词，则用掩码替换参数值。
     * 如果参数值是 Map 类型，会递归过滤 Map 中的敏感字段。
     * </p>
     *
     * @param paramName  参数名
     * @param paramValue 参数值
     * @return 过滤后的参数值
     */
    private Object filterSensitiveParam(String paramName, Object paramValue) {
        if (paramValue == null) {
            return null;
        }

        // 检查参数名是否为敏感字段
        if (isSensitiveField(paramName)) {
            return SENSITIVE_VALUE_MASK;
        }

        // 如果参数值是 Map，递归过滤其中的敏感字段
        if (paramValue instanceof Map<?, ?>) {
            return filterMapSensitiveFields((Map<?, ?>) paramValue);
        }

        return paramValue;
    }

    /**
     * 过滤 Map 中的敏感字段
     * <p>
     * 遍历 Map 的所有键，如果键名包含敏感关键词，则用掩码替换对应的值。
     * </p>
     *
     * @param map 原始 Map
     * @return 过滤后的新 Map
     */
    private Map<String, Object> filterMapSensitiveFields(Map<?, ?> map) {
        Map<String, Object> filtered = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isSensitiveField(key)) {
                filtered.put(key, SENSITIVE_VALUE_MASK);
            } else {
                filtered.put(key, entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * 判断字段名是否为敏感字段
     * <p>
     * 检查字段名（不区分大小写）是否包含敏感关键词。
     * </p>
     *
     * @param fieldName 字段名
     * @return 如果是敏感字段返回 true，否则返回 false
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取方法参数名（带缓存）
     * <p>
     * 使用 ConcurrentHashMap 缓存已解析的参数名，避免重复解析。
     * </p>
     *
     * @param method 方法对象
     * @return 参数名数组，如果无法获取则返回 null
     */
    private String[] getParameterNames(Method method) {
        return parameterNamesCache.computeIfAbsent(method, discoverer::getParameterNames);
    }
}
