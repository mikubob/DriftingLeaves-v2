package com.xuan.constant;


/**
 * 监控常量
 */
public class MonitorConstant {

    public static final String RESOURCE_ALL = "all";
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 60;
    public static final int DEFAULT_SAMPLE_INTERVAL_SECONDS = 3;
    public static final String STATUS_SMOOTH = "运行流畅";
    public static final String STATUS_NORMAL = "运行正常";
    public static final String STATUS_BUSY = "运行繁忙";
    public static final String STATUS_HIGH = "运行压力较高";

    private MonitorConstant() {
    }
}
