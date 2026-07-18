package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 访客记录VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VisitorRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 设备指纹
    private String visitorFingerprint;

    // 当前会话ID
    private String sessionId;

    // 访客在数据库中的ID
    private Long visitorId;

    // 是否是新访客
    private Boolean isNewVisitor;
}
