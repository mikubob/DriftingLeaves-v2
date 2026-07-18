package com.xuan.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志分页查询返回VO
 * 基于前端 OperationLog/index.vue 实际使用字段设计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogQueryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 日志ID
    private Long id;

    // 操作类型
    private String operationType;

    // 操作对象
    private String operationTarget;

    // 目标ID
    private Long targetId;

    // 操作数据
    private String operateData;

    // 操作结果
    private Integer result;

    // 错误信息
    private String errorMessage;

    // 操作时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;
}
