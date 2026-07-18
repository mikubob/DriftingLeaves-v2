package com.xuan.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class IpUtil {

    private static Ip2Region ip2RegionV4;
    private static Ip2Region ip2RegionV6;

    // 静态加载 ip2region IPv4 数据库
    static {
        try (InputStream inputStream = IpUtil.class.getClassLoader().getResourceAsStream("ip2region_v4.xdb")) {
            if (inputStream != null) {
                Config v4Config = Config.custom()
                        .setCachePolicy(Config.BufferCache)
                        .setXdbInputStream(inputStream)
                        .asV4();
                ip2RegionV4 = Ip2Region.create(v4Config, null);
            } else {
                log.error("无法找到 ip2region_v4.xdb 文件");
            }
        } catch (Exception e) {
            log.error("ip2region IPv4 数据库初始化失败", e);
        }
    }

    // 静态加载 ip2region IPv6 数据库
    static {
        try (InputStream inputStream = IpUtil.class.getClassLoader().getResourceAsStream("ip2region_v6.xdb")) {
            if (inputStream != null) {
                Config v6Config = Config.custom()
                        .setCachePolicy(Config.BufferCache)
                        .setXdbInputStream(inputStream)
                        .asV6();
                ip2RegionV6 = Ip2Region.create(v6Config, null);
            } else {
                log.error("无法找到 ip2region_v6.xdb 文件");
            }
        } catch (Exception e) {
            log.error("ip2region IPv6 数据库初始化失败", e);
        }
    }

    /**
     * 获取真实客户端 IP
     * 当前未使用 CDN，后端仅通过 Nginx 暴露，因此只信任 Nginx 设置的代理头
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";

        // 1. Nginx 等可信代理设置的请求头
        String ip = request.getHeader("X-Real-IP");
        if (isInvalid(ip)) ip = request.getHeader("X-Forwarded-For");
        if (isInvalid(ip)) ip = request.getHeader("Proxy-Client-IP");
        if (isInvalid(ip)) ip = request.getHeader("WL-Proxy-Client-IP");

        // 2. 直连 IP
        if (isInvalid(ip)) ip = request.getRemoteAddr();

        // 3. 处理多级代理 (取第一个)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // 4. 本地回环处理
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }

        return ip;
    }



    /**
     * 获取地理位置 (离线高性能版)
     */
    public static String getIpLocation(String ip) {
        if (isInvalid(ip) || "127.0.0.1".equals(ip)) return "本地";

        Ip2Region ip2Region = getIp2RegionByType(ip);
        if (ip2Region == null) return "未知";

        try {
            String region = ip2Region.search(ip);
            if (region != null) {
                Map<String, String> geoInfo = parseGeoInfo(region);
                StringBuilder sb = new StringBuilder();
                String province = geoInfo.getOrDefault("province", "");
                String city = geoInfo.getOrDefault("city", "");

                // 格式: 省份 城市
                if (!province.isEmpty()) sb.append(province);
                if (!city.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append(city);
                }
                return !sb.isEmpty() ? sb.toString() : "未知";
            }
        } catch (Exception e) {
            log.error("IP 地理位置解析失败", e);
        }
        return "未知";
    }

    /**
     * 获取地理位置信息
     * 返回包含 country、province 和 city 的 Map
     *
     * @param ip IP 地址
     * @return 包含 country、province 和 city 的 Map
     */
    public static Map<String, String> getGeoInfo(String ip) {
        if (isInvalid(ip) || "127.0.0.1".equals(ip)) {
            return createEmptyResult();
        }

        Ip2Region ip2Region = getIp2RegionByType(ip);
        if (ip2Region == null) {
            return createEmptyResult();
        }

        try {
            String region = ip2Region.search(ip);
            if (region != null) {
                return parseGeoInfo(region);
            }
        } catch (Exception e) {
            log.error("IP 地理位置解析失败", e);
        }
        return createEmptyResult();
    }

    /**
     * 根据 IP 类型获取对应的 ip2region 查询服务
     * IPv4 使用 v4 xdb，IPv6 使用 v6 xdb
     *
     * @param ip IP 地址
     * @return 对应的 ip2region 查询服务
     */
    private static Ip2Region getIp2RegionByType(String ip) {
        // IPv6 地址包含冒号，优先走 IPv6 数据库
        if (ip != null && ip.contains(":")) {
            return ip2RegionV6;
        }

        // 默认按 IPv4 处理
        return ip2RegionV4;
    }

    /**
     * 解析 ip2region 返回的地理位置信息
     * 按照官方当前内置数据格式：国家|省份|城市|ISP|国家代码
     *
     * @param region ip2region 返回字符串
     * @return 包含 country、province 和 city 的 Map
     */
    private static Map<String, String> parseGeoInfo(String region) {
        Map<String, String> result = createEmptyResult();
        if (region == null || region.isEmpty()) {
            return result;
        }

        String[] parts = region.split("\\|");

        // 官方当前内置数据格式：国家|省份|城市|ISP|国家代码
        // 这里仍然只提取 country、province、city，保持项目原来的展示效果
        String country = parts.length > 0 ? normalizePart(parts[0]) : "";
        String province = parts.length > 1 ? normalizePart(parts[1]) : "";
        String city = parts.length > 2 ? normalizePart(parts[2]) : "";

        result.put("country", country);
        result.put("province", province);
        result.put("city", city);
        return result;
    }

    /**
     * 标准化 ip2region 字段值
     * 过滤空串和 0，避免无效值参与展示
     *
     * @param value 原始字段值
     * @return 标准化后的字段值
     */
    private static String normalizePart(String value) {
        if (value == null) {
            return "";
        }

        value = value.trim();
        return "0".equals(value) ? "" : value;
    }

    // 提取公共方法避免重复代码
    private static Map<String, String> createEmptyResult() {
        Map<String, String> result = new HashMap<>();
        result.put("country", "");
        result.put("province", "");
        result.put("city", "");
        return result;
    }

    private static boolean isInvalid(String ip) {
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return true;
        }
        // 简单 IPv4/IPv6 验证
        if (!ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") &&
                !ip.matches("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$")) {
            return true;
        }
        return false;
    }

    /**
     * 关闭 ip2region 查询服务资源
     */
    public static void close() {
        if (ip2RegionV4 != null) {
            try {
                ip2RegionV4.close();
            } catch (Exception e) {
                log.error("关闭 ip2region IPv4 查询服务失败", e);
            }
        }

        if (ip2RegionV6 != null) {
            try {
                ip2RegionV6.close();
            } catch (Exception e) {
                log.error("关闭 ip2region IPv6 查询服务失败", e);
            }
        }
    }
}
