package com.xuan.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.Browser;
import cn.hutool.http.useragent.OS;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.xuan.service.UserAgentService;
import org.springframework.stereotype.Service;

/**
 * 基于 Hutool 的 UserAgent 解析服务实现
 * 已移除 commons-lang3 依赖，完全使用 Hutool 和 JDK 原生方法
 */
@Service
public class UserAgentServiceImpl implements UserAgentService {

    /**
     * 获取操作系统名称
     * @param userAgent 用户代理字符串
     * @return 系统名称 (如: Windows, macOS, Android, iOS, Linux)
     */
    @Override
    public String getOsName(String userAgent) {
        // 使用 Hutool StrUtil 进行空值检查
        if (StrUtil.isBlank(userAgent)) {
            return "Unknown";
        }

        // 使用 Hutool 解析
        UserAgent ua = UserAgentUtil.parse(userAgent);
        
        if (ua == null) {
            return "Unknown";
        }

        // 获取操作系统对象
        OS os = ua.getOs();
        
        if (os == null) {
            return "Unknown";
        }

        // 获取原始名称
        String osName = os.getName();
        
        if (StrUtil.isBlank(osName)) {
            return "Unknown";
        }

        // 标准化返回名称
        if (osName.startsWith("Windows")) {
            return "Windows";
        }
        
        // 处理 Mac 系列 (macOS, Mac OS X)
        if (osName.startsWith("Mac")) {
            // 针对 iPadOS 13+ 可能伪装成 Mac 的情况做二次确认
            // 如果 UA 中包含 iPad 但 OS 识别为 Mac，根据业务需求可强制返回 iOS
            // 这里保持 Hutool 的默认行为，通常新版 Hutool 能较好区分
            if (userAgent.contains("iPad") || userAgent.contains("iPhone")) {
                 // 如果确定是移动设备但被识别为 Mac，可取消下面这行的注释强制转为 iOS
                 // return "iOS"; 
            }
            return "macOS";
        }

        // 处理 iOS
        if ("iOS".equals(osName) || "iPhone OS".equals(osName) || osName.contains("iPhone") || osName.contains("iPad")) {
            return "iOS";
        }

        // 处理 Android
        if ("Android".equals(osName) || osName.contains("Android")) {
            return "Android";
        }

        // 处理 FreeBSD（需在 Linux 之前判断）
        if (osName.contains("FreeBSD") || userAgent.toLowerCase().contains("freebsd")) {
            return "FreeBSD";
        }

        // 处理 Ubuntu（需在 Linux 之前判断）
        if (osName.contains("Ubuntu") || userAgent.toLowerCase().contains("ubuntu")) {
            return "Ubuntu";
        }

        // 处理 Linux (排除 Android)
        if (osName.contains("Linux")) {
            return "Linux";
        }

        // 其他情况返回原始名称，如果是 "Unknown" 则统一返回
        return "Unknown".equals(osName) ? "Unknown" : osName;
    }

    /**
     * 获取浏览器名称
     * @param userAgent 用户代理字符串
     * @return 浏览器名称 (如: Chrome, Firefox, Safari, Edge, WeChat)
     */
    @Override
    public String getBrowserName(String userAgent) {
        if (StrUtil.isBlank(userAgent)) {
            return "Unknown";
        }

        // 转小写用于关键词匹配 (JDK 原生方法)
        String uaLower = userAgent.toLowerCase();

        // 1. 优先处理特殊客户端 (微信, QQ, 支付宝, 钉钉等)
        // 这些通常内核是 Chrome，但需要识别为特定 App
        if (uaLower.contains("micromessenger")) {
            return "WeChat";
        }
        // 区分 QQ 浏览器 (排除微信环境)
        if (uaLower.contains("qq/") && !uaLower.contains("micromessenger")) {
            return "QQ";
        }
        if (uaLower.contains("alipayclient")) {
            return "Alipay";
        }
        if (uaLower.contains("dingtalk")) {
            return "DingTalk";
        }

        // 2. 使用 Hutool 解析常规浏览器
        UserAgent ua = UserAgentUtil.parse(userAgent);
        
        if (ua == null) {
            return "Unknown";
        }

        Browser browser = ua.getBrowser();
        
        if (browser == null) {
            return "Unknown";
        }

        String browserName = browser.getName();

        if (StrUtil.isBlank(browserName)) {
            return "Unknown";
        }

        // 3. 标准化映射 (使用 switch 表达式，Java 14+)
        // 如果使用的是 Java 8，请改用 if-else 或 switch-case
        return switch (browserName.toLowerCase()) {
            case "chrome" -> "Chrome";
            case "firefox" -> "Firefox";
            case "safari" -> "Safari";
            case "edge" -> "Edge";
            case "ie" -> "IE";
            case "opera" -> "Opera";
            case "micromessenger" -> "WeChat"; // 兜底：防止 Hutool 直接识别出 MicroMessenger
            default -> {
                String lower = browserName.toLowerCase();
                if (lower.contains("360")) {
                    yield "360Browser";
                }
                if (lower.contains("sogou")) {
                    yield "Sogou";
                }
                if (lower.contains("uc") || lower.contains("ucbrowser")) {
                    yield "UCBrowser";
                }
                if (lower.contains("quark")) {
                    yield "Quark";
                }
                if (lower.contains("baidu") || lower.contains("bidu")) {
                    yield "Baidu";
                }
                if (lower.contains("maxthon")) {
                    yield "Maxthon";
                }
                if (lower.contains("2345")) {
                    yield "2345Browser";
                }
                if (lower.contains("liebao")) {
                    yield "Liebao";
                }
                yield browserName;
            }
        };
    }
}