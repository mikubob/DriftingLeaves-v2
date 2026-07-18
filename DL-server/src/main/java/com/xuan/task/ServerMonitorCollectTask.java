package com.xuan.task;

import com.xuan.constant.MonitorConstant;
import com.xuan.constant.RedisConstant;
import com.xuan.service.impl.monitor.ServerMonitorUtil;
import com.xuan.utils.MetricFormatUtil;
import com.xuan.vo.DiskDetailVO;
import com.xuan.vo.DiskIoDetailVO;
import com.xuan.vo.MetricPointVO;
import com.xuan.vo.NetworkDetailVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务监控定时采集任务
 * <p>
 * 这是整个监控模块唯一的“采集入口”：
 * 1. 周期性采集 overview/load/cpu/memory/disk/network/diskIo 当前快照
 * 2. 将快照写入 Redis 的 current 层缓存
 * 3. 顺手维护 network / diskIo 的趋势点列表
 * <p>
 * 查询接口绝不能再次做现场采样，否则会污染这里维护的采样基线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerMonitorCollectTask {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 服务监控相关缓存统一写入 Redis
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 应用启动后先主动采一轮，避免前端首次打开页面时拿不到 current 快照
     */
    @PostConstruct
    public void init() {
        collectTrendMetrics();
    }

    /**
     * 周期性采集监控快照
     * <p>
     * 采集到的 overview/load/cpu/memory 全局快照与各资源维度拆分详情，
     * 统一写入 Redis current 层缓存，并维护网络与磁盘 IO 的趋势点列表。
     */
    @Scheduled(fixedRate = MonitorConstant.DEFAULT_SAMPLE_INTERVAL_SECONDS * 1000L, initialDelay = 3000L)
    public void collectTrendMetrics() {
        try {
            // 1. 生成统一采集时间，保证同一批数据的 collectTime 一致
            String collectTime = LocalDateTime.now().format(DATETIME_FORMATTER);

            // 2. 一次性采集整批监控数据
            ServerMonitorUtil.ServerMonitorCurrentSnapshot snapshot = ServerMonitorUtil.collectCurrentSnapshot(collectTime);

            // 3. 写入全局 current 快照
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_OVERVIEW, snapshot.overview());
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_LOAD, snapshot.load());
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_CPU, snapshot.cpu());
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_MEMORY, snapshot.memory());
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_DISK_OPTIONS, snapshot.diskOptions());
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_NETWORK_OPTIONS, snapshot.networkOptions());
            redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_DISK_IO_OPTIONS, snapshot.diskIoOptions());

            // 4. 写入按资源维度拆分的 current 快照，并维护趋势点
            for (DiskDetailVO detailVO : snapshot.diskDetails().values()) {
                redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_DISK_PREFIX + detailVO.getResourceName(), detailVO);
            }
            for (NetworkDetailVO detailVO : snapshot.networkDetails().values()) {
                redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_NETWORK_PREFIX + detailVO.getResourceName(), detailVO);
                appendNetworkPoint(detailVO);
            }
            for (DiskIoDetailVO detailVO : snapshot.diskIoDetails().values()) {
                redisTemplate.opsForValue().set(RedisConstant.SERVER_MONITOR_CURRENT_DISK_IO_PREFIX + detailVO.getResourceName(), detailVO);
                appendDiskIoPoint(detailVO);
            }
        } catch (Exception e) {
            log.error("collect server monitor snapshot failed", e);
        }
    }

    /**
     * 追加网络趋势点
     * <p>
     * 将网络详情中的上下行速率转换为 KB/s 后追加到 Redis 趋势列表。
     *
     * @param detailVO 网络详情
     */
    private void appendNetworkPoint(NetworkDetailVO detailVO) {
        MetricPointVO pointVO = MetricPointVO.builder()
                .time(LocalTime.now().format(TIME_FORMATTER))
                .inValue(MetricFormatUtil.toKb(detailVO.getDownBytesPerSec()))
                .outValue(MetricFormatUtil.toKb(detailVO.getUpBytesPerSec()))
                .build();
        appendPoint(RedisConstant.SERVER_MONITOR_NETWORK_TIMELINE_PREFIX + detailVO.getResourceName(), pointVO);
    }

    /**
     * 追加磁盘 IO 趋势点
     * <p>
     * 将磁盘 IO 详情中的读写速率转换为 KB/s 后追加到 Redis 趋势列表。
     *
     * @param detailVO 磁盘 IO 详情
     */
    private void appendDiskIoPoint(DiskIoDetailVO detailVO) {
        MetricPointVO pointVO = MetricPointVO.builder()
                .time(LocalTime.now().format(TIME_FORMATTER))
                .readValue(MetricFormatUtil.toKb(detailVO.getReadBytesPerSec()))
                .writeValue(MetricFormatUtil.toKb(detailVO.getWriteBytesPerSec()))
                .build();
        appendPoint(RedisConstant.SERVER_MONITOR_DISK_IO_TIMELINE_PREFIX + detailVO.getResourceName(), pointVO);
    }

    /**
     * 向指定趋势列表尾部追加一个点位，并裁剪到最大长度
     *
     * @param key     Redis Key
     * @param pointVO 趋势点数据
     */
    private void appendPoint(String key, MetricPointVO pointVO) {
        Object value = redisTemplate.opsForValue().get(key);
        List<MetricPointVO> points;
        if (value instanceof List<?> rawList) {
            points = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof MetricPointVO metricPointVO) {
                    points.add(metricPointVO);
                }
            }
        } else {
            points = new ArrayList<>();
        }
        points.add(pointVO);
        while (points.size() > MonitorConstant.MAX_LIMIT) {
            points.removeFirst();
        }
        redisTemplate.opsForValue().set(key, points);
    }
}
