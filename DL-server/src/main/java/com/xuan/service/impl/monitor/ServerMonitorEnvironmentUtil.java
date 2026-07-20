package com.xuan.service.impl.monitor;

import com.xuan.enumeration.DeploymentModeEnum;
import com.xuan.properties.ServerMonitorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 服务监控运行环境识别工具
 * <p>
 * 专门负责回答一个问题：
 * “当前监控数据到底应该按宿主机视角解释，还是按容器视角解释？”
 * <p>
 * 识别优先级如下：
 * 1. application.yml 中显式配置
 * 2. 环境变量显式覆盖
 * 3. 自动检测容器特征
 */
@Slf4j
@Component
public class ServerMonitorEnvironmentUtil {

    /**
     * 环境变量覆盖项
     */
    private static final String ENV_DEPLOYMENT_MODE = "SERVER_MONITOR_DEPLOYMENT_MODE";

    /**
     * /proc/1/cgroup 中常见的容器运行时标记
     */
    private static final List<String> CONTAINER_CGROUP_MARKERS = List.of("docker", "containerd", "kubepods", "podman");

    private final ServerMonitorProperties properties;

    public ServerMonitorEnvironmentUtil(ServerMonitorProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析最终部署模式
     * <p>
     * 优先级：
     * 1. 显式配置
     * 2. 环境变量
     * 3. 自动检测
     *
     * @return 最终部署模式
     */
    public DeploymentModeEnum resolveDeploymentMode() {
        // 1. 先看配置文件是否显式指定
        DeploymentModeEnum configured = DeploymentModeEnum.fromValue(properties.getDeploymentMode());
        if (configured != null) {
            return configured;
        }

        // 2. 再看环境变量是否显式覆盖
        DeploymentModeEnum envMode = DeploymentModeEnum.fromValue(System.getenv(ENV_DEPLOYMENT_MODE));
        if (envMode != null) {
            return envMode;
        }

        // 3. 都没有时，退回自动检测
        return detectContainer() ? DeploymentModeEnum.CONTAINER : DeploymentModeEnum.SERVER;
    }

    /**
     * 构造部署模式可读文本
     *
     * @param mode 部署模式
     * @return 文本说明
     */
    public String buildDeploymentModeText(DeploymentModeEnum mode) {
        return mode == null ? null : mode.getText();
    }

    /**
     * 构造部署模式提示信息
     * <p>
     * 这些提示会直接返回给前端，用于告诉用户：
     * 当前看到的是整机视角，还是容器可见资源视角。
     *
     * @param mode 部署模式
     * @return 提示信息列表
     */
    public List<String> buildDeploymentTips(DeploymentModeEnum mode) {
        if (mode == DeploymentModeEnum.CONTAINER) {
            List<String> tips = new ArrayList<>();
            tips.add("当前服务运行在 Docker 容器内，监控数据更偏向容器可见资源。");
            tips.add("磁盘、网络与磁盘 IO 指标不一定代表整台宿主机。");
            return tips;
        }
        return List.of("当前服务运行在服务器宿主机上，监控数据代表整机可见资源。");
    }

    /**
     * 是否处于容器模式
     *
     * @return true 表示容器模式
     */
    public boolean isContainerMode() {
        return resolveDeploymentMode() == DeploymentModeEnum.CONTAINER;
    }

    /**
     * 判断某个挂载点是否属于容器常见噪音挂载
     * <p>
     * 如 /etc/hosts、/etc/hostname、/etc/resolv.conf 这类挂载，
     * 往往只是容器运行时自动注入，不适合作为“真实磁盘资源”展示。
     *
     * @param resourceName 资源名/挂载点
     * @return true 表示应过滤或降级处理
     */
    public boolean isContainerNoiseMount(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            return false;
        }
        String normalized = resourceName.toLowerCase(Locale.ROOT);
        return normalized.equals("/etc/hosts")
                || normalized.equals("/etc/hostname")
                || normalized.equals("/etc/resolv.conf");
    }

    /**
     * 自动检测当前是否为容器环境
     *
     * @return true 表示检测为容器环境
     */
    private boolean detectContainer() {
        return hasDockerEnvFile() || cgroupLooksLikeContainer();
    }

    /**
     * Docker 容器内通常会存在 /.dockerenv 文件
     */
    private boolean hasDockerEnvFile() {
        return Files.exists(Path.of("/.dockerenv"));
    }

    /**
     * 读取 /proc/1/cgroup，判断是否存在容器运行时关键字
     * <p>
     * 这里读取的是 PID 1 的 cgroup 信息，因为它最能代表当前运行命名空间的环境特征。
     */
    private boolean cgroupLooksLikeContainer() {
        try {
            Path path = Path.of("/proc/1/cgroup");
            if (!Files.exists(path)) {
                return false;
            }
            String content = Files.readString(path).toLowerCase(Locale.ROOT);
            for (String marker : CONTAINER_CGROUP_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("读取 /proc/1/cgroup 失败，跳过容器 cgroup 检测", e);
        }
        return false;
    }
}
