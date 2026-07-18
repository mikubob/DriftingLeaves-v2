package com.xuan.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("operation_logs")
public class OperationLogs implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 操作用户ID
    private Long userId;

    // 操作类型
    private String operationType;

    // 操作目标
    private String operationTarget;

    // 目标 ID
    private Long targetId;

    // 操作数据
    private String operateData;

    // 操作结果，0-失败，1-成功
    private Integer result;

    // 错误信息
    private String errorMessage;

    // 操作时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operationTime;

    // 操作IP
    private String ipAddress;
}
