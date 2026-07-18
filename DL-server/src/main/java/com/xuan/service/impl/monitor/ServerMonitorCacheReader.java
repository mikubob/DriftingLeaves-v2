package com.xuan.service.impl.monitor;

import com.xuan.vo.MetricPointVO;
import com.xuan.vo.OptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务监控缓存读取组件
 * <p>
 * 该类职责非常单一：
 * 1. 从 Redis 读取 current 快照
 * 2. 从 Redis 读取趋势点
 * 3. 从 Redis 读取资源选项
 * <p>
 * 它不参与任何现场采样，也不会推进 CPU / Network / Disk IO 的采样基线，
 * 从而保证查询接口是真正的“纯读缓存”。
 */
@Component
@RequiredArgsConstructor
public class ServerMonitorCacheReader {

    /**
     * 服务监控相关缓存统一从 Redis 读取
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 读取单个对象型 current 快照
     *
     * @param key  Redis Key
     * @param type 目标类型
     * @return 成功则返回目标对象，失败或类型不匹配则返回 null
     * @param <T>  目标泛型
     */
    public <T> T readObject(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * 读取资源选项列表
     *
     * @param key Redis Key
     * @return 资源选项列表；缓存为空或类型不匹配时返回空集合
     */
    public List<OptionVO> readOptions(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionVO> options = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof OptionVO optionVO) {
                options.add(optionVO);
            }
        }
        return options;
    }

    /**
     * 读取趋势点并按 limit 截取最近的 N 个点
     *
     * @param key   Redis Key
     * @param limit 需要返回的最大点位数量
     * @return 最近的趋势点列表
     */
    public List<MetricPointVO> readPoints(String key, int limit) {
        Object value = redisTemplate.opsForValue().get(key);
        if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        int fromIndex = Math.max(0, rawList.size() - limit);
        List<MetricPointVO> points = new ArrayList<>(rawList.size() - fromIndex);
        for (int i = fromIndex; i < rawList.size(); i++) {
            Object item = rawList.get(i);
            if (item instanceof MetricPointVO pointVO) {
                points.add(pointVO);
            }
        }
        return points;
    }
}
