package com.xuan.service;

import com.xuan.dto.ServerMonitorQueryDTO;
import com.xuan.vo.CpuDetailVO;
import com.xuan.vo.DiskDetailVO;
import com.xuan.vo.DiskIoDetailVO;
import com.xuan.vo.LoadDetailVO;
import com.xuan.vo.MemoryDetailVO;
import com.xuan.vo.NetworkDetailVO;
import com.xuan.vo.OptionVO;
import com.xuan.vo.ServerMonitorOverviewVO;
import com.xuan.vo.ServerMonitorSnapshotVO;

import java.util.List;

/**
 * 服务监控服务
 */
public interface IServerMonitorService {

    /**
     * 获取服务概览
     * @return 服务概览
     */
    ServerMonitorOverviewVO getOverview();

    /**
     * 获取负载详情
     * @return 负载详情
     */
    LoadDetailVO getLoadDetail();

    /**
     * 获取 CPU 详情
     * @return CPU 详情
     */
    CpuDetailVO getCpuDetail();

    /**
     * 获取内存详情
     * @return 内存详情
     */
    MemoryDetailVO getMemoryDetail();

    /**
     * 获取磁盘资源选项列表
     * @return 磁盘资源选项列表
     */
    List<OptionVO> getDiskOptions();

    /**
     * 根据查询条件获取磁盘详情
     * @param queryDTO 查询参数
     * @return 磁盘详情
     */
    DiskDetailVO getDiskDetail(ServerMonitorQueryDTO queryDTO);

    /**
     * 获取网络资源选项列表
     * @return 网络资源选项列表
     */
    List<OptionVO> getNetworkOptions();

    /**
     * 根据查询条件获取网络详情
     * @param queryDTO 查询参数
     * @return 网络详情
     */
    NetworkDetailVO getNetworkDetail(ServerMonitorQueryDTO queryDTO);

    /**
     * 获取磁盘 IO 资源选项列表
     * @return 磁盘 IO 资源选项列表
     */
    List<OptionVO> getDiskIoOptions();

    /**
     * 根据查询条件获取磁盘 IO 详情
     * @param queryDTO 查询参数
     * @return 磁盘 IO 详情
     */
    DiskIoDetailVO getDiskIoDetail(ServerMonitorQueryDTO queryDTO);

    /**
     * 获取监控聚合快照
     * @param queryDTO 查询参数
     * @return 监控聚合快照
     */
    ServerMonitorSnapshotVO getSnapshot(ServerMonitorQueryDTO queryDTO);
}
