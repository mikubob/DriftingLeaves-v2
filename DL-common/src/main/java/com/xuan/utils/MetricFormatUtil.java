package com.xuan.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 监控数据格式化工具类
 */
public class MetricFormatUtil {

    /**
     * 单位转换
     */
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    private static final long TB = GB * 1024L;

    /**
     * 私有构造方法, 避免实例化
     */
    private MetricFormatUtil() {
    }

    /**
     * 百分比格式化
     * @param value 值
     * @return 百分比
     */
    public static BigDecimal percent(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
    /**
     * 安全百分比格式化
     * @param used 已使用
     * @param total 总数
     * @return 百分比
     */
    public static BigDecimal safePercent(long used, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(used * 100D / total).setScale(2, RoundingMode.HALF_UP);
    }
    /**
     * 字节数转换为KB
     * @param bytes 字节数
     * @return KB
     */
    public static BigDecimal toKb(long bytes) {
        return BigDecimal.valueOf(bytes / 1024D).setScale(1, RoundingMode.HALF_UP);
    }
    /**
     * 字节数格式化
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            bytes = 0;
        }
        if (bytes >= TB) {
            return decimal(bytes, TB) + " TB";
        }
        if (bytes >= GB) {
            return decimal(bytes, GB) + " GB";
        }
        if (bytes >= MB) {
            return decimal(bytes, MB) + " MB";
        }
        if (bytes >= KB) {
            return decimal(bytes, KB) + " KB";
        }
        return bytes + " B";
    }
    /**
     * 字节数格式化为速率
     * @param bytesPerSecond 字节数/秒
     * @return 格式化后的字符串
     */
    public static String formatRate(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }

    /**
     * 字节数格式化
     * @param bytes 字节数
     * @param unit 单位
     * @return 格式化后的字符串
     */
    private static String decimal(long bytes, long unit) {
        return BigDecimal.valueOf(bytes)
                .divide(BigDecimal.valueOf(unit), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
