package com.xuan.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 服务监控聚合快照 VO
 * <p>
 * 该对象是前端“服务器监测”页面的主消费对象，
 * 会把顶部概览、各详情模块、资源选项、部署模式信息一次性返回给前端。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServerMonitorSnapshotVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前整批快照的统一采集时间
     */
    private String collectTime;

    /**
     * 当前部署模式：
     * 1. server：宿主机视角
     * 2. container：容器视角
     */
    private String deploymentMode;

    /**
     * 部署模式可读文本
     */
    private String deploymentModeText;

    /**
     * 当前部署模式下的提示信息
     * 前端可直接用于页面顶部说明或帮助文案展示
     */
    private List<String> deploymentTips;

    /**
     * 顶部概览信息
     */
    private ServerMonitorOverviewVO overview;

    /**
     * 负载详情
     */
    private LoadDetailVO load;

    /**
     * CPU 详情
     */
    private CpuDetailVO cpu;

    /**
     * 内存详情
     */
    private MemoryDetailVO memory;

    /**
     * 磁盘资源下拉选项
     */
    private List<OptionVO> diskOptions;

    /**
     * 网络资源下拉选项
     */
    private List<OptionVO> networkOptions;

    /**
     * 磁盘 IO 资源下拉选项
     */
    private List<OptionVO> diskIoOptions;

    /**
     * 当前选中的磁盘详情
     */
    private DiskDetailVO disk;

    /**
     * 当前选中的网络详情
     */
    private NetworkDetailVO network;

    /**
     * 当前选中的磁盘 IO 详情
     */
    private DiskIoDetailVO diskIo;
}
