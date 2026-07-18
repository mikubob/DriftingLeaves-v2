package com.xuan.service.impl.monitor;

import cn.hutool.system.oshi.OshiUtil;
import com.xuan.constant.MonitorConstant;
import com.xuan.utils.MetricFormatUtil;
import com.xuan.vo.CpuCoreUsageVO;
import com.xuan.vo.CpuDetailVO;
import com.xuan.vo.DiskDetailVO;
import com.xuan.vo.DiskIoDetailVO;
import com.xuan.vo.InodeUsageVO;
import com.xuan.vo.LoadDetailVO;
import com.xuan.vo.MemoryDetailVO;
import com.xuan.vo.NetworkDetailVO;
import com.xuan.vo.OptionVO;
import com.xuan.vo.ProcessMetricVO;
import com.xuan.vo.ServerMonitorOverviewVO;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 服务监控底层采集与组装工具类
 * <p>
 * 该类直接与 OSHI 交互，负责：
 * 1. 采集 CPU / 内存 / 磁盘 / 网络 / 磁盘 IO 原始指标
 * 2. 维护 CPU、网络、磁盘 IO 的差分采样基线
 * 3. 将底层采样结果统一组装为前端需要的 VO
 * <p>
 * 注意：
 * collectCpuAggregate / collectNetworkSnapshot / collectDiskIoSnapshot
 * 都会推进内部采样基线，因此只能由定时任务统一调用，不能在查询接口中现场调用。
 */
public class ServerMonitorUtil {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final CentralProcessor PROCESSOR = OshiUtil.getProcessor();
    private static final GlobalMemory MEMORY = OshiUtil.getMemory();
    private static final OperatingSystem OPERATING_SYSTEM = OshiUtil.getOs();

    /**
     * CPU 总负载差分基线
     */
    private static long[] lastSystemTicks = PROCESSOR.getSystemCpuLoadTicks();

    /**
     * CPU 每核心差分基线
     */
    private static long[][] lastProcessorTicks = PROCESSOR.getProcessorCpuLoadTicks();

    /**
     * 网络吞吐差分基线
     * key 通常为 all 或网卡名
     */
    private static final Map<String, CounterSnapshot> NETWORK_SNAPSHOTS = new HashMap<>();

    /**
     * 磁盘 IO 差分基线
     * key 通常为 all 或磁盘名
     */
    private static final Map<String, DiskCounterSnapshot> DISK_SNAPSHOTS = new HashMap<>();

    private ServerMonitorUtil() {
    }

    /**
     * 一次性采集整批当前监控快照
     * <p>
     * 这是定时任务的核心入口，返回值同时包含：
     * 1. overview/load/cpu/memory 四块全局快照
     * 2. disk/network/diskIo 的资源选项
     * 3. 各资源维度拆分后的 current 详情
     *
     * @param collectTime 本次采集时间
     * @return 当前整批监控快照
     */
    public static ServerMonitorCurrentSnapshot collectCurrentSnapshot(String collectTime) {
        // 1. 先采集 CPU 聚合数据，确保 overview/load/cpu 三块共用同一批底层样本
        CpuAggregate cpuAggregate = collectCpuAggregate();
        MemorySnapshot memorySnapshot = getMemorySnapshot();
        DiskUsageSnapshot allDiskUsage = getDiskUsage(MonitorConstant.RESOURCE_ALL);

        // 2. 组装 overview/load/cpu/memory 四块全局快照
        ServerMonitorOverviewVO overview = ServerMonitorOverviewVO.builder()
                .loadPercent(cpuAggregate.loadSnapshot().loadPercent())
                .loadStatusText(statusText(cpuAggregate.loadSnapshot().loadPercent()))
                .cpuPercent(cpuAggregate.cpuPercent())
                .cpuCoreCount(PROCESSOR.getLogicalProcessorCount())
                .memoryUsedText(MetricFormatUtil.formatBytes(memorySnapshot.usedBytes()))
                .memoryTotalText(MetricFormatUtil.formatBytes(memorySnapshot.totalBytes()))
                .memoryPercent(memorySnapshot.usagePercent())
                .diskUsedText(MetricFormatUtil.formatBytes(allDiskUsage.used()))
                .diskTotalText(MetricFormatUtil.formatBytes(allDiskUsage.total()))
                .diskPercent(allDiskUsage.usagePercent())
                .collectTime(collectTime)
                .build();

        LoadDetailVO load = buildLoadDetail(cpuAggregate, collectTime);
        CpuDetailVO cpu = buildCpuDetail(cpuAggregate, collectTime);
        MemoryDetailVO memory = buildMemoryDetail(memorySnapshot, collectTime);

        // 3. 列举所有资源选项
        List<OptionVO> diskOptions = listDiskOptions();
        List<OptionVO> networkOptions = listNetworkOptions();
        List<OptionVO> diskIoOptions = listDiskIoOptions();

        // 4. 采集各磁盘 current 快照
        Map<String, DiskDetailVO> diskDetails = new LinkedHashMap<>();
        for (OptionVO option : diskOptions) {
            DiskDetailVO detailVO = buildDiskDetail(getDiskUsage(option.getValue()), collectTime);
            diskDetails.put(detailVO.getResourceName(), detailVO);
        }

        // 5. 采集各网络 current 快照
        Map<String, NetworkDetailVO> networkDetails = new LinkedHashMap<>();
        for (OptionVO option : networkOptions) {
            NetworkDetailVO detailVO = buildNetworkDetail(collectNetworkSnapshot(option.getValue()), collectTime);
            networkDetails.put(detailVO.getResourceName(), detailVO);
        }

        // 6. 采集各磁盘 IO current 快照
        Map<String, DiskIoDetailVO> diskIoDetails = new LinkedHashMap<>();
        for (OptionVO option : diskIoOptions) {
            DiskIoDetailVO detailVO = buildDiskIoDetail(collectDiskIoSnapshot(option.getValue()), collectTime);
            diskIoDetails.put(detailVO.getResourceName(), detailVO);
        }

        return new ServerMonitorCurrentSnapshot(
                overview,
                load,
                cpu,
                memory,
                diskOptions,
                networkOptions,
                diskIoOptions,
                diskDetails,
                networkDetails,
                diskIoDetails
        );
    }

    /**
     * 根据 CPU 聚合样本派生负载详情 VO
     */
    private static LoadDetailVO buildLoadDetail(CpuAggregate cpuAggregate, String collectTime) {
        List<OSProcess> processes = OPERATING_SYSTEM.getProcesses();
        long runningCount = processes.stream().filter(process -> process.getState() == OSProcess.State.RUNNING).count();
        CpuTicksSnapshot ticksSnapshot = cpuAggregate.ticksSnapshot();
        return LoadDetailVO.builder()
                .loadPercent(cpuAggregate.loadSnapshot().loadPercent())
                .statusText(statusText(cpuAggregate.loadSnapshot().loadPercent()))
                .loadAverage1m(cpuAggregate.loadSnapshot().load1m())
                .loadAverage5m(cpuAggregate.loadSnapshot().load5m())
                .loadAverage15m(cpuAggregate.loadSnapshot().load15m())
                .runningProcessCount((int) runningCount)
                .totalProcessCount(OPERATING_SYSTEM.getProcessCount())
                .userPercent(MetricFormatUtil.percent(ticksSnapshot.user()))
                .nicePercent(MetricFormatUtil.percent(ticksSnapshot.nice()))
                .systemPercent(MetricFormatUtil.percent(ticksSnapshot.system()))
                .idlePercent(MetricFormatUtil.percent(ticksSnapshot.idle()))
                .iowaitPercent(MetricFormatUtil.percent(ticksSnapshot.iowait()))
                .irqPercent(MetricFormatUtil.percent(ticksSnapshot.irq()))
                .softirqPercent(MetricFormatUtil.percent(ticksSnapshot.softIrq()))
                .stealPercent(MetricFormatUtil.percent(ticksSnapshot.steal()))
                .guestPercent(MetricFormatUtil.percent(ticksSnapshot.guest()))
                .guestNicePercent(MetricFormatUtil.percent(ticksSnapshot.guestNice()))
                .topProcesses(topProcesses(OperatingSystem.ProcessSorting.CPU_DESC))
                .collectTime(collectTime)
                .build();
    }

    /**
     * 根据 CPU 聚合样本派生 CPU 详情 VO
     */
    private static CpuDetailVO buildCpuDetail(CpuAggregate cpuAggregate, String collectTime) {
        return CpuDetailVO.builder()
                .cpuPercent(cpuAggregate.cpuPercent())
                .cpuModel(PROCESSOR.getProcessorIdentifier().getName())
                .physicalPackageCount(PROCESSOR.getPhysicalPackageCount())
                .physicalCoreCount(PROCESSOR.getPhysicalProcessorCount())
                .logicalCoreCount(PROCESSOR.getLogicalProcessorCount())
                .coreUsages(cpuAggregate.coreUsages())
                .topProcesses(topProcesses(OperatingSystem.ProcessSorting.CPU_DESC))
                .collectTime(collectTime)
                .build();
    }

    /**
     * 组装内存详情 VO
     * <p>
     * 当前 shared / buffer cache 在部分平台上不易稳定获取，
     * 因此底层样本中允许为 null，由上层根据部署模式再决定是否展示。
     */
    private static MemoryDetailVO buildMemoryDetail(MemorySnapshot snapshot, String collectTime) {
        long swapUsed = safeLong(MEMORY.getVirtualMemory().getSwapUsed());
        long swapTotal = safeLong(MEMORY.getVirtualMemory().getSwapTotal());
        return MemoryDetailVO.builder()
                .memoryPercent(snapshot.usagePercent())
                .usedBytes(snapshot.usedBytes())
                .totalBytes(snapshot.totalBytes())
                .availableBytes(snapshot.availableBytes())
                .freeBytes(snapshot.freeBytes())
                .sharedBytes(snapshot.sharedBytes())
                .bufferCacheBytes(snapshot.bufferCacheBytes())
                .swapUsedBytes(swapUsed)
                .swapTotalBytes(swapTotal)
                .usedText(MetricFormatUtil.formatBytes(snapshot.usedBytes()))
                .totalText(MetricFormatUtil.formatBytes(snapshot.totalBytes()))
                .availableText(MetricFormatUtil.formatBytes(snapshot.availableBytes()))
                .freeText(formatNullableBytes(snapshot.freeBytes()))
                .sharedText(formatNullableBytes(snapshot.sharedBytes()))
                .bufferCacheText(formatNullableBytes(snapshot.bufferCacheBytes()))
                .swapText(MetricFormatUtil.formatBytes(swapUsed) + "/" + MetricFormatUtil.formatBytes(swapTotal))
                .topProcesses(topProcesses(OperatingSystem.ProcessSorting.RSS_DESC))
                .collectTime(collectTime)
                .build();
    }

    /**
     * 组装磁盘详情 VO
     */
    private static DiskDetailVO buildDiskDetail(DiskUsageSnapshot snapshot, String collectTime) {
        InodeUsageVO inodeUsageVO = null;
        if (snapshot.inodeTotal() > 0L) {
            inodeUsageVO = InodeUsageVO.builder()
                    .total(snapshot.inodeTotal())
                    .used(snapshot.inodeUsed())
                    .available(snapshot.inodeAvailable())
                    .usagePercent(MetricFormatUtil.safePercent(snapshot.inodeUsed(), snapshot.inodeTotal()))
                    .build();
        }
        return DiskDetailVO.builder()
                .resourceName(snapshot.resourceName())
                .mount(snapshot.mount())
                .fileSystem(snapshot.fileSystem())
                .type(snapshot.type())
                .totalText(MetricFormatUtil.formatBytes(snapshot.total()))
                .usedText(MetricFormatUtil.formatBytes(snapshot.used()))
                .availableText(MetricFormatUtil.formatBytes(snapshot.available()))
                .diskSizeText(MetricFormatUtil.formatBytes(snapshot.total()))
                .usagePercent(snapshot.usagePercent())
                .inode(inodeUsageVO)
                .collectTime(collectTime)
                .build();
    }

    /**
     * 组装网络详情 VO
     */
    private static NetworkDetailVO buildNetworkDetail(NetworkRateSnapshot snapshot, String collectTime) {
        return NetworkDetailVO.builder()
                .resourceName(snapshot.resourceName())
                .upBytesPerSec(snapshot.outBytesPerSec())
                .downBytesPerSec(snapshot.inBytesPerSec())
                .upText(MetricFormatUtil.formatRate(snapshot.outBytesPerSec()))
                .downText(MetricFormatUtil.formatRate(snapshot.inBytesPerSec()))
                .totalSentText(MetricFormatUtil.formatBytes(snapshot.totalSent()))
                .totalRecvText(MetricFormatUtil.formatBytes(snapshot.totalRecv()))
                .collectTime(collectTime)
                .build();
    }

    /**
     * 组装磁盘 IO 详情 VO
     */
    private static DiskIoDetailVO buildDiskIoDetail(DiskIoSnapshot snapshot, String collectTime) {
        return DiskIoDetailVO.builder()
                .resourceName(snapshot.resourceName())
                .readBytesPerSec(snapshot.readBytesPerSec())
                .writeBytesPerSec(snapshot.writeBytesPerSec())
                .readText(MetricFormatUtil.formatRate(snapshot.readBytesPerSec()))
                .writeText(MetricFormatUtil.formatRate(snapshot.writeBytesPerSec()))
                .opsPerSec(snapshot.opsPerSec())
                .awaitMs(snapshot.awaitMs())
                .collectTime(collectTime)
                .build();
    }

    /**
     * 列举磁盘资源选项
     * <p>
     * 默认先放入 all，后续再追加各文件系统挂载点。
     */
    public static List<OptionVO> listDiskOptions() {
        List<OptionVO> options = new ArrayList<>();
        options.add(OptionVO.builder().label("全部").value(MonitorConstant.RESOURCE_ALL).build());
        FileSystem fileSystem = OPERATING_SYSTEM.getFileSystem();
        for (OSFileStore fileStore : fileSystem.getFileStores()) {
            String mount = blankToDefault(fileStore.getMount(), fileStore.getName());
            if (options.stream().noneMatch(option -> option.getValue().equals(mount))) {
                options.add(OptionVO.builder().label(mount).value(mount).build());
            }
        }
        return options;
    }

    /**
     * 列举网络资源选项
     */
    public static List<OptionVO> listNetworkOptions() {
        List<OptionVO> options = new ArrayList<>();
        options.add(OptionVO.builder().label("全部").value(MonitorConstant.RESOURCE_ALL).build());
        for (NetworkIF networkIF : OshiUtil.getNetworkIFs()) {
            String name = networkIF.getName();
            if (name != null && !name.isBlank()) {
                options.add(OptionVO.builder().label(name).value(name).build());
            }
        }
        return options;
    }

    /**
     * 列举磁盘 IO 资源选项
     * <p>
     * 这里使用 OSHI 可见的 HWDiskStore 名称。
     */
    public static List<OptionVO> listDiskIoOptions() {
        List<OptionVO> options = new ArrayList<>();
        options.add(OptionVO.builder().label("全部").value(MonitorConstant.RESOURCE_ALL).build());
        for (HWDiskStore diskStore : OshiUtil.getDiskStores()) {
            String name = diskStore.getName();
            if (name != null && !name.isBlank() && options.stream().noneMatch(option -> option.getValue().equals(name))) {
                options.add(OptionVO.builder().label(name).value(name).build());
            }
        }
        return options;
    }

    /**
     * 采集网络吞吐快照
     * <p>
     * 该方法会推进 NETWORK_SNAPSHOTS 中保存的差分基线，
     * 因此只能由定时任务统一调用，不适合查询接口现场调用。
     */
    public static synchronized NetworkRateSnapshot collectNetworkSnapshot(String resourceName) {
        List<NetworkIF> networkIFS = OshiUtil.getNetworkIFs();
        for (NetworkIF networkIF : networkIFS) {
            networkIF.updateAttributes();
        }
        if (MonitorConstant.RESOURCE_ALL.equalsIgnoreCase(defaultResource(resourceName))) {
            // 聚合 all 视角：将所有网卡累计计数求和
            long totalRecv = 0L;
            long totalSent = 0L;
            long now = System.currentTimeMillis();
            for (NetworkIF networkIF : networkIFS) {
                totalRecv += safeLong(networkIF.getBytesRecv());
                totalSent += safeLong(networkIF.getBytesSent());
            }
            CounterSnapshot previous = NETWORK_SNAPSHOTS.put(MonitorConstant.RESOURCE_ALL, new CounterSnapshot(totalRecv, totalSent, now));
            long inBytesPerSec = calculateRate(previous == null ? 0L : previous.inBytes(), totalRecv, previous == null ? now : previous.timestamp(), now);
            long outBytesPerSec = calculateRate(previous == null ? 0L : previous.outBytes(), totalSent, previous == null ? now : previous.timestamp(), now);
            return new NetworkRateSnapshot(MonitorConstant.RESOURCE_ALL, inBytesPerSec, outBytesPerSec, totalRecv, totalSent);
        }

        // 单网卡视角：只采集指定网卡
        NetworkIF target = networkIFS.stream()
                .filter(networkIF -> Objects.equals(networkIF.getName(), resourceName))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return new NetworkRateSnapshot(defaultResource(resourceName), 0L, 0L, 0L, 0L);
        }
        long now = System.currentTimeMillis();
        long totalRecv = safeLong(target.getBytesRecv());
        long totalSent = safeLong(target.getBytesSent());
        CounterSnapshot previous = NETWORK_SNAPSHOTS.put(target.getName(), new CounterSnapshot(totalRecv, totalSent, now));
        long inBytesPerSec = calculateRate(previous == null ? 0L : previous.inBytes(), totalRecv, previous == null ? now : previous.timestamp(), now);
        long outBytesPerSec = calculateRate(previous == null ? 0L : previous.outBytes(), totalSent, previous == null ? now : previous.timestamp(), now);
        return new NetworkRateSnapshot(target.getName(), inBytesPerSec, outBytesPerSec, totalRecv, totalSent);
    }

    /**
     * 采集磁盘 IO 快照
     * <p>
     * 与网络采样类似，该方法同样会推进 DISK_SNAPSHOTS 中保存的差分基线。
     */
    public static synchronized DiskIoSnapshot collectDiskIoSnapshot(String resourceName) {
        List<HWDiskStore> diskStores = OshiUtil.getDiskStores();
        for (HWDiskStore diskStore : diskStores) {
            diskStore.updateAttributes();
        }
        if (MonitorConstant.RESOURCE_ALL.equalsIgnoreCase(defaultResource(resourceName))) {
            // 聚合 all 视角：将所有磁盘块设备的累计计数汇总
            long readBytes = 0L;
            long writeBytes = 0L;
            long reads = 0L;
            long writes = 0L;
            long transferTime = 0L;
            long now = System.currentTimeMillis();
            for (HWDiskStore diskStore : diskStores) {
                readBytes += safeLong(diskStore.getReadBytes());
                writeBytes += safeLong(diskStore.getWriteBytes());
                reads += safeLong(diskStore.getReads());
                writes += safeLong(diskStore.getWrites());
                transferTime += safeLong(diskStore.getTransferTime());
            }
            DiskCounterSnapshot current = new DiskCounterSnapshot(readBytes, writeBytes, reads, writes, transferTime, now);
            DiskCounterSnapshot previous = DISK_SNAPSHOTS.put(MonitorConstant.RESOURCE_ALL, current);
            return buildDiskIoSnapshot(MonitorConstant.RESOURCE_ALL, previous, current);
        }

        // 单磁盘视角：只采集指定块设备
        HWDiskStore target = diskStores.stream()
                .filter(diskStore -> Objects.equals(diskStore.getName(), resourceName))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return new DiskIoSnapshot(defaultResource(resourceName), 0L, 0L, 0, 0);
        }
        long now = System.currentTimeMillis();
        DiskCounterSnapshot current = new DiskCounterSnapshot(
                safeLong(target.getReadBytes()),
                safeLong(target.getWriteBytes()),
                safeLong(target.getReads()),
                safeLong(target.getWrites()),
                safeLong(target.getTransferTime()),
                now
        );
        DiskCounterSnapshot previous = DISK_SNAPSHOTS.put(target.getName(), current);
        return buildDiskIoSnapshot(target.getName(), previous, current);
    }

    /**
     * 根据前后两次采样结果构造磁盘 IO 差分结果
     */
    private static DiskIoSnapshot buildDiskIoSnapshot(String resourceName, DiskCounterSnapshot previous, DiskCounterSnapshot current) {
        if (previous == null) {
            // 第一次采样没有历史基线，无法计算速率，统一按 0 返回
            return new DiskIoSnapshot(resourceName, 0L, 0L, 0, 0);
        }
        long readBytesPerSec = calculateRate(previous.readBytes(), current.readBytes(), previous.timestamp(), current.timestamp());
        long writeBytesPerSec = calculateRate(previous.writeBytes(), current.writeBytes(), previous.timestamp(), current.timestamp());
        long seconds = Math.max(1L, (current.timestamp() - previous.timestamp()) / 1000L);
        long opsDelta = Math.max(0L, (current.reads() - previous.reads()) + (current.writes() - previous.writes()));
        int opsPerSec = (int) (opsDelta / seconds);
        int awaitMs = opsDelta <= 0L ? 0 : (int) Math.max(0L, (current.transferTime() - previous.transferTime()) / opsDelta);
        return new DiskIoSnapshot(resourceName, readBytesPerSec, writeBytesPerSec, opsPerSec, awaitMs);
    }

    /**
     * 获取资源占用最高的前 N 个进程
     */
    private static List<ProcessMetricVO> topProcesses(Comparator<OSProcess> processSort) {
        List<OSProcess> processes = OPERATING_SYSTEM.getProcesses(null, processSort, 5);
        long totalMemory = Math.max(1L, MEMORY.getTotal());
        List<ProcessMetricVO> list = new ArrayList<>(processes.size());
        for (OSProcess process : processes) {
            list.add(ProcessMetricVO.builder()
                    .pid((long) process.getProcessID())
                    .processName(process.getName())
                    .cpuPercent(MetricFormatUtil.percent(process.getProcessCpuLoadCumulative() * 100D))
                    .memoryPercent(MetricFormatUtil.percent(process.getResidentMemory() * 100D / totalMemory))
                    .build());
        }
        return list;
    }

    /**
     * 采集 CPU 聚合样本
     * <p>
     * 这是整个监控模块中最关键的一步：
     * 1. 同时拿到总 CPU、各核心 CPU、ticks 分布
     * 2. 由同一批底层样本派生 overview/load/cpu
     * 3. 避免这些模块各自独立采样导致页面数据不一致
     */
    private static CpuAggregate collectCpuAggregate() {
        synchronized (ServerMonitorUtil.class) {
            long[] currentTicks = PROCESSOR.getSystemCpuLoadTicks();
            long[][] currentProcessorTicks = PROCESSOR.getProcessorCpuLoadTicks();
            long[] previousSystemTicks = lastSystemTicks;
            long[][] previousProcessorTicks = lastProcessorTicks;
            lastSystemTicks = currentTicks;
            lastProcessorTicks = currentProcessorTicks;

            // 1. 计算总 CPU 两次采样之间的 tick 差值
            long[] diff = diffTicks(previousSystemTicks, currentTicks);
            long total = sum(diff);
            if (total <= 0L) {
                total = 1L;
            }

            // 2. 计算总 CPU 使用率与各 tick 类型分布
            double busy = total - diff[CentralProcessor.TickType.IDLE.getIndex()];
            BigDecimal cpuPercent = MetricFormatUtil.percent(Math.max(0D, busy * 100D / total));
            CpuTicksSnapshot ticksSnapshot = new CpuTicksSnapshot(
                    diff[CentralProcessor.TickType.USER.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.NICE.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.SYSTEM.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.IDLE.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.IOWAIT.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.IRQ.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.SOFTIRQ.getIndex()] * 100D / total,
                    diff[CentralProcessor.TickType.STEAL.getIndex()] * 100D / total,
                    0D,
                    0D
            );

            // 3. 逐核计算各核心使用率
            List<CpuCoreUsageVO> coreUsages = new ArrayList<>(currentProcessorTicks.length);
            for (int i = 0; i < currentProcessorTicks.length; i++) {
                long[] previousCoreTicks = i < previousProcessorTicks.length ? previousProcessorTicks[i] : new long[currentProcessorTicks[i].length];
                long[] diffCore = diffTicks(previousCoreTicks, currentProcessorTicks[i]);
                long totalCore = sum(diffCore);
                if (totalCore <= 0L) {
                    totalCore = 1L;
                }
                double busyCore = totalCore - diffCore[CentralProcessor.TickType.IDLE.getIndex()];
                coreUsages.add(CpuCoreUsageVO.builder()
                        .coreLabel("核心 " + (i + 1))
                        .usagePercent(MetricFormatUtil.percent(Math.max(0D, busyCore * 100D / totalCore)))
                        .build());
            }

            // 4. 顺手构造 load 样本，保证 CPU 与 load 复用同一批底层数据
            return new CpuAggregate(cpuPercent, coreUsages, ticksSnapshot, getLoadSnapshot(cpuPercent));
        }
    }

    /**
     * 计算两次 ticks 的差值
     */
    private static long[] diffTicks(long[] previousTicks, long[] currentTicks) {
        int length = Math.min(previousTicks.length, currentTicks.length);
        long[] diff = new long[length];
        for (int i = 0; i < length; i++) {
            diff[i] = Math.max(0L, currentTicks[i] - previousTicks[i]);
        }
        return diff;
    }

    /**
     * 求和工具方法
     */
    private static long sum(long[] values) {
        long total = 0L;
        for (long value : values) {
            total += value;
        }
        return total;
    }

    /**
     * 采集系统负载样本
     * <p>
     * 1. Linux/Unix 上优先使用 load average
     * 2. 如果当前平台不支持 load average，则 loadPercent 退回 CPU 百分比
     * 3. 但 1m/5m/15m 本身返回 null，而不是伪装成 0
     */
    private static LoadSnapshot getLoadSnapshot(BigDecimal fallbackCpuPercent) {
        double[] averages = PROCESSOR.getSystemLoadAverage(3);
        double logicalCoreCount = Math.max(1, PROCESSOR.getLogicalProcessorCount());
        double load1m = averages.length > 0 ? averages[0] : -1D;
        double load5m = averages.length > 1 ? averages[1] : -1D;
        double load15m = averages.length > 2 ? averages[2] : -1D;
        BigDecimal loadPercent = load1m >= 0D
                ? MetricFormatUtil.percent(load1m * 100D / logicalCoreCount)
                : fallbackCpuPercent;
        return new LoadSnapshot(loadPercent, decimalOrNull(load1m), decimalOrNull(load5m), decimalOrNull(load15m));
    }

    /**
     * 采集内存样本
     * <p>
     * 当前先保证 total/available/used 的可靠性；
     * 更细粒度字段由上层根据平台能力和部署模式决定是否展示。
     */
    private static MemorySnapshot getMemorySnapshot() {
        long total = MEMORY.getTotal();
        long available = MEMORY.getAvailable();
        long used = Math.max(0L, total - available);
        return new MemorySnapshot(total, available, used, null, null, null, MetricFormatUtil.safePercent(used, total));
    }

    /**
     * 采集磁盘使用样本
     * <p>
     * 1. resourceName=all 时返回聚合结果
     * 2. 指定资源时优先匹配 mount/name/volume
     */
    private static DiskUsageSnapshot getDiskUsage(String resourceName) {
        FileSystem fileSystem = OPERATING_SYSTEM.getFileSystem();
        List<OSFileStore> stores = fileSystem.getFileStores();
        if (!MonitorConstant.RESOURCE_ALL.equalsIgnoreCase(defaultResource(resourceName))) {
            OSFileStore target = stores.stream()
                    .filter(store -> matchDiskResource(store, resourceName))
                    .findFirst()
                    .orElse(null);
            if (target != null) {
                target.updateAttributes();
                long total = safeLong(target.getTotalSpace());
                long available = safeLong(target.getUsableSpace());
                long used = Math.max(0L, total - available);
                long inodeTotal = safeLong(target.getTotalInodes());
                long inodeFree = safeLong(target.getFreeInodes());
                return new DiskUsageSnapshot(
                        resourceName,
                        blankToDefault(target.getMount(), target.getName()),
                        blankToDefault(target.getName(), target.getVolume()),
                        blankToDefault(target.getType(), "unknown"),
                        total,
                        available,
                        used,
                        inodeTotal,
                        Math.max(0L, inodeTotal - inodeFree),
                        inodeFree,
                        MetricFormatUtil.safePercent(used, total)
                );
            }
        }

        // 聚合 all 视角：汇总所有文件系统可见容量
        long total = 0L;
        long available = 0L;
        long inodeTotal = 0L;
        long inodeFree = 0L;
        for (OSFileStore store : stores) {
            store.updateAttributes();
            total += safeLong(store.getTotalSpace());
            available += safeLong(store.getUsableSpace());
            if (store.getTotalInodes() > 0L) {
                inodeTotal += safeLong(store.getTotalInodes());
                inodeFree += safeLong(store.getFreeInodes());
            }
        }
        long used = Math.max(0L, total - available);
        return new DiskUsageSnapshot(
                MonitorConstant.RESOURCE_ALL,
                MonitorConstant.RESOURCE_ALL,
                "ALL",
                "MULTI",
                total,
                available,
                used,
                inodeTotal,
                Math.max(0L, inodeTotal - inodeFree),
                inodeFree,
                MetricFormatUtil.safePercent(used, total)
        );
    }

    /**
     * 判断文件系统资源是否命中指定资源名
     */
    private static boolean matchDiskResource(OSFileStore store, String resourceName) {
        return Objects.equals(store.getMount(), resourceName)
                || Objects.equals(store.getName(), resourceName)
                || Objects.equals(store.getVolume(), resourceName);
    }

    /**
     * 根据负载百分比返回状态文案
     */
    private static String statusText(BigDecimal loadPercent) {
        double value = loadPercent == null ? 0D : loadPercent.doubleValue();
        if (value < 30D) {
            return MonitorConstant.STATUS_SMOOTH;
        }
        if (value < 60D) {
            return MonitorConstant.STATUS_NORMAL;
        }
        if (value < 85D) {
            return MonitorConstant.STATUS_BUSY;
        }
        return MonitorConstant.STATUS_HIGH;
    }

    /**
     * 将不支持的平台返回值（通常为负数）统一转为 null
     */
    private static BigDecimal decimalOrNull(double value) {
        if (value < 0D) {
            return null;
        }
        return MetricFormatUtil.percent(value);
    }

    /**
     * 计算速率：
     * (当前累计值 - 上次累计值) / 时间间隔
     */
    private static long calculateRate(long previousValue, long currentValue, long previousTimestamp, long currentTimestamp) {
        long intervalMs = Math.max(1L, currentTimestamp - previousTimestamp);
        long delta = Math.max(0L, currentValue - previousValue);
        return delta * 1000L / intervalMs;
    }

    /**
     * 可空字节值格式化
     */
    private static String formatNullableBytes(Long value) {
        return value == null ? null : MetricFormatUtil.formatBytes(value);
    }

    /**
     * 将空资源名统一折叠为 all
     */
    private static String defaultResource(String resourceName) {
        return resourceName == null || resourceName.isBlank() ? MonitorConstant.RESOURCE_ALL : resourceName;
    }

    /**
     * 空白字符串兜底
     */
    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * long 值防负数保护
     */
    private static long safeLong(long value) {
        return Math.max(0L, value);
    }

    /**
     * 网络速率采样结果
     */
    public record NetworkRateSnapshot(String resourceName, long inBytesPerSec, long outBytesPerSec,
                                      long totalRecv, long totalSent) {
    }

    /**
     * 磁盘 IO 采样结果
     */
    public record DiskIoSnapshot(String resourceName, long readBytesPerSec, long writeBytesPerSec,
                                 int opsPerSec, int awaitMs) {
    }

    private record CounterSnapshot(long inBytes, long outBytes, long timestamp) {
    }

    private record DiskCounterSnapshot(long readBytes, long writeBytes, long reads, long writes,
                                       long transferTime, long timestamp) {
    }

    /**
     * 负载样本
     */
    private record LoadSnapshot(BigDecimal loadPercent, BigDecimal load1m, BigDecimal load5m, BigDecimal load15m) {
    }

    /**
     * 内存样本
     */
    private record MemorySnapshot(long totalBytes, long availableBytes, long usedBytes,
                                  Long freeBytes, Long sharedBytes, Long bufferCacheBytes,
                                  BigDecimal usagePercent) {
    }

    /**
     * 磁盘使用样本
     */
    private record DiskUsageSnapshot(String resourceName, String mount, String fileSystem, String type,
                                     long total, long available, long used,
                                     long inodeTotal, long inodeUsed, long inodeAvailable,
                                     BigDecimal usagePercent) {
    }

    /**
     * CPU ticks 分布样本
     */
    private record CpuTicksSnapshot(double user, double nice, double system, double idle, double iowait,
                                    double irq, double softIrq, double steal, double guest, double guestNice) {
    }

    /**
     * CPU 聚合样本
     * <p>
     * 统一承载 overview/load/cpu 所需的公共底层数据。
     */
    private record CpuAggregate(BigDecimal cpuPercent, List<CpuCoreUsageVO> coreUsages,
                                CpuTicksSnapshot ticksSnapshot, LoadSnapshot loadSnapshot) {
    }

    /**
     * 当前整批监控快照
     * <p>
     * 定时任务采集完成后会把该对象拆分写入 Redis current 层缓存。
     */
    public record ServerMonitorCurrentSnapshot(ServerMonitorOverviewVO overview,
                                               LoadDetailVO load,
                                               CpuDetailVO cpu,
                                               MemoryDetailVO memory,
                                               List<OptionVO> diskOptions,
                                               List<OptionVO> networkOptions,
                                               List<OptionVO> diskIoOptions,
                                               Map<String, DiskDetailVO> diskDetails,
                                               Map<String, NetworkDetailVO> networkDetails,
                                               Map<String, DiskIoDetailVO> diskIoDetails) {
    }
}
