package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 网络详情VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NetworkDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 资源名称
    private String resourceName;

    // 每秒上传字节数
    private Long upBytesPerSec;

    // 每秒下载字节数
    private Long downBytesPerSec;

    // 上传速度文本
    private String upText;

    // 下载速度文本
    private String downText;

    // 总发送量文本
    private String totalSentText;

    // 总接收量文本
    private String totalRecvText;

    // 指标点列表
    private List<MetricPointVO> points;

    // 采集时间
    private String collectTime;
}
