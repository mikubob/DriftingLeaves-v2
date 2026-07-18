package com.xuan.enumeration;

import lombok.Getter;

/**
 * 服务监控部署模式枚举
 * <p>
 * 用于明确区分：
 * 1. 当前监控数据来自整台服务器宿主机
 * 2. 当前监控数据来自 Docker 容器可见视角
 * <p>
 * 前端会根据该枚举对应的 value/text 自动切换展示文案与提示信息。
 */
@Getter
public enum DeploymentModeEnum {

    /**
     * 宿主机视角
     */
    SERVER("server", "服务器宿主机视角"),

    /**
     * Docker 容器视角
     */
    CONTAINER("container", "Docker 容器视角");

    private final String value;
    private final String text;

    DeploymentModeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据配置值/环境变量值解析部署模式
     * <p>
     * 兼容两种写法：
     * 1. server / container
     * 2. SERVER / CONTAINER
     *
     * @param value 配置值
     * @return 解析成功返回枚举，失败返回 null
     */
    public static DeploymentModeEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeploymentModeEnum mode : values()) {
            if (mode.value.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
