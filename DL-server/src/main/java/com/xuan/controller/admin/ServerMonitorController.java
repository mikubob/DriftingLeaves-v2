package com.xuan.controller.admin;

import com.xuan.dto.ServerMonitorQueryDTO;
import com.xuan.result.Result;
import com.xuan.service.IServerMonitorService;
import com.xuan.vo.CpuDetailVO;
import com.xuan.vo.DiskDetailVO;
import com.xuan.vo.DiskIoDetailVO;
import com.xuan.vo.LoadDetailVO;
import com.xuan.vo.MemoryDetailVO;
import com.xuan.vo.NetworkDetailVO;
import com.xuan.vo.OptionVO;
import com.xuan.vo.ServerMonitorOverviewVO;
import com.xuan.vo.ServerMonitorSnapshotVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端服务器监控相关接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN 可访问。服务器监控包含 CPU/内存/磁盘/网络等敏感信息，
 * AUDITOR（审计员）和 AUTHOR（创作者）均不可访问。
 * </p>
 * <p>
 * 阶段三改造说明：
 * <ul>
 *     <li>原 checkAdminOnly() 私有方法已删除（基于 BaseContext.getCurrentRole() 判断的旧逻辑）</li>
 *     <li>权限校验统一由类级 @PreAuthorize("hasRole('ADMIN')") 接管，与 Spring Security 体系一致</li>
 *     <li>不再依赖 BaseContext，符合"完全移除 BaseContext"的阶段三目标</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/server-monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ServerMonitorController {

    private final IServerMonitorService serverMonitorService;

    /**
     * 获取服务器监控概览数据
     * @return 概览数据
     */
    @GetMapping("/overview")
    public Result<ServerMonitorOverviewVO> overview() {
        log.info("获取服务器监控概览数据");
        return Result.success(serverMonitorService.getOverview());
    }

    /**
     * 获取服务器负载详情
     * @return 负载详情
     */
    @GetMapping("/load")
    public Result<LoadDetailVO> load() {
        log.info("获取服务器负载详情");
        return Result.success(serverMonitorService.getLoadDetail());
    }

    /**
     * 获取服务器CPU详情
     * @return CPU详情
     */
    @GetMapping("/cpu")
    public Result<CpuDetailVO> cpu() {
        log.info("获取服务器CPU详情");
        return Result.success(serverMonitorService.getCpuDetail());
    }

    /**
     * 获取服务器内存详情
     * @return 内存详情
     */
    @GetMapping("/memory")
    public Result<MemoryDetailVO> memory() {
        log.info("获取服务器内存详情");
        return Result.success(serverMonitorService.getMemoryDetail());
    }

    /**
     * 获取服务器磁盘选项
     * @return 磁盘选项
     */
    @GetMapping("/disk/options")
    public Result<List<OptionVO>> diskOptions() {
        log.info("获取服务器磁盘选项");
        return Result.success(serverMonitorService.getDiskOptions());
    }

    /**
     * 获取服务器磁盘详情
     * @param queryDTO 查询条件
     * @return 磁盘详情
     */
    @GetMapping("/disk")
    public Result<DiskDetailVO> disk(ServerMonitorQueryDTO queryDTO) {
        log.info("获取服务器磁盘详情, {}", queryDTO);
        return Result.success(serverMonitorService.getDiskDetail(queryDTO));
    }

    /**
     * 获取服务器网络选项
     * @return 网络选项
     */
    @GetMapping("/network/options")
    public Result<List<OptionVO>> networkOptions() {
        log.info("获取服务器网络选项");
        return Result.success(serverMonitorService.getNetworkOptions());
    }

    /**
     * 获取服务器网络详情
     * @param queryDTO 查询条件
     * @return 网络详情
     */
    @GetMapping("/network")
    public Result<NetworkDetailVO> network(ServerMonitorQueryDTO queryDTO) {
        log.info("获取服务器网络详情, {}", queryDTO);
        return Result.success(serverMonitorService.getNetworkDetail(queryDTO));
    }

    /**
     * 获取服务器磁盘IO选项
     * @return 磁盘IO选项
     */
    @GetMapping("/disk-io/options")
    public Result<List<OptionVO>> diskIoOptions() {
        log.info("获取服务器磁盘IO选项");
        return Result.success(serverMonitorService.getDiskIoOptions());
    }

    /**
     * 获取服务器磁盘IO详情
     * @param queryDTO 查询条件
     * @return 磁盘IO详情
     */
    @GetMapping("/disk-io")
    public Result<DiskIoDetailVO> diskIo(ServerMonitorQueryDTO queryDTO) {
        log.info("获取服务器磁盘IO详情, {}", queryDTO);
        return Result.success(serverMonitorService.getDiskIoDetail(queryDTO));
    }

    /**
     * 获取服务器监控聚合快照
     * @param queryDTO 查询条件
     * @return 聚合快照
     */
    @GetMapping("/snapshot")
    public Result<ServerMonitorSnapshotVO> snapshot(ServerMonitorQueryDTO queryDTO) {
        log.info("获取服务器监控聚合快照, {}", queryDTO);
        return Result.success(serverMonitorService.getSnapshot(queryDTO));
    }
}
