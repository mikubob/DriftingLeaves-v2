package com.xuan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 服务监控模块配置项
 * <p>
 * 当前主要控制“部署模式”的识别优先级：
 * 1. auto：自动检测当前是宿主机还是容器
 * 2. server：强制按宿主机模式处理
 * 3. container：强制按容器模式处理
 */
@Component
@ConfigurationProperties(prefix = "server-monitor")
@Data
public class ServerMonitorProperties {

    /**
     * 部署模式
     * <p>
     * 可选值：
     * 1. auto
     * 2. server
     * 3. container
     */
    private String deploymentMode = "auto";
}
