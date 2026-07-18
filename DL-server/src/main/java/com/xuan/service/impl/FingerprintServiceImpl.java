package com.xuan.service.impl;


import com.xuan.dto.VisitorRecordDTO;
import com.xuan.service.FingerprintService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FingerprintServiceImpl implements FingerprintService {

    // 预编译正则，提高性能
    private static final Pattern CHROME_PATTERN = Pattern.compile("chrome/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIREFOX_PATTERN = Pattern.compile("firefox/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFARI_PATTERN = Pattern.compile("version/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDGE_PATTERN = Pattern.compile("edg[ea]?(\\d+)", Pattern.CASE_INSENSITIVE); // 兼容 Edge 和 Edg
    private static final Pattern OPERA_PATTERN = Pattern.compile("opr/(\\d+)", Pattern.CASE_INSENSITIVE);

    @Override
    public String generateVisitorFingerprint(VisitorRecordDTO dto, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");

        String simplifiedPlatform = simplifyPlatform(dto.getPlatform());
        String screenGroup = groupScreenResolution(dto.getScreen());
        
        Integer hardwareConcurrency = dto.getHardwareConcurrency() != null ? dto.getHardwareConcurrency() : 0;
        Integer deviceMemory = dto.getDeviceMemory() != null ? dto.getDeviceMemory() : 0;

        String language = StringUtils.hasText(dto.getLanguage()) ? dto.getLanguage() : "unknown";
        String timezone = StringUtils.hasText(dto.getTimezone()) ? dto.getTimezone() : "unknown";
        
        // 优化：只提取浏览器类型，去掉版本号，防止因浏览器自动升级导致指纹失效
        // 如果业务强依赖版本区分，可改回 extractBrowserInfoWithVersion
        String browserType = extractBrowserType(userAgent);

        String fingerprintSource = String.format("%s|%s|%s|%s|%d|%d|%s",
                simplifiedPlatform,
                language,
                timezone,
                screenGroup,
                hardwareConcurrency,
                deviceMemory,
                browserType
        );

        if (log.isDebugEnabled()) {
            log.debug("Fingerprint source: {}", fingerprintSource);
        }

        return DigestUtils.md5DigestAsHex(fingerprintSource.getBytes());
    }

    /**
     * 仅提取浏览器类型，不包含版本号，以提高指纹稳定性
     */
    private String extractBrowserType(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "Unknown";
        }

        String lowerUa = userAgent.toLowerCase();

        // 注意判断顺序：Edge 通常在 UA 中也包含 Chrome，Opera 也包含 Chrome
        if (lowerUa.contains("edg") || lowerUa.contains("edge")) {
            return "Edge";
        }
        if (lowerUa.contains("opr") || lowerUa.contains("opera")) {
            return "Opera";
        }
        // Chrome 排除 Edge 和 Opera (上面已经排除了)
        if (lowerUa.contains("chrome")) {
            return "Chrome";
        }
        if (lowerUa.contains("firefox")) {
            return "Firefox";
        }
        // Safari 通常不包含 Chrome
        if (lowerUa.contains("safari")) {
            return "Safari";
        }

        return "Other";
    }

    /**
     * 如果需要带版本号的逻辑，可以使用正则优化版
     */
    private String extractBrowserInfoWithVersion(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "Unknown_0";
        }

        if (userAgent.toLowerCase().contains("edg") || userAgent.toLowerCase().contains("edge")) {
            return "Edge_" + extractVersion(userAgent, EDGE_PATTERN);
        }
        if (userAgent.toLowerCase().contains("opr") || userAgent.toLowerCase().contains("opera")) {
            return "Opera_" + extractVersion(userAgent, OPERA_PATTERN);
        }
        if (userAgent.toLowerCase().contains("chrome")) {
            return "Chrome_" + extractVersion(userAgent, CHROME_PATTERN);
        }
        if (userAgent.toLowerCase().contains("firefox")) {
            return "Firefox_" + extractVersion(userAgent, FIREFOX_PATTERN);
        }
        if (userAgent.toLowerCase().contains("safari")) {
            return "Safari_" + extractVersion(userAgent, SAFARI_PATTERN);
        }

        return "Other_0";
    }

    private String extractVersion(String ua, Pattern pattern) {
        Matcher matcher = pattern.matcher(ua);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "0";
    }

    private String simplifyPlatform(String platform) {
        if (!StringUtils.hasText(platform)) return "unknown";
        String lower = platform.toLowerCase();
        if (lower.contains("win")) return "Windows";
        if (lower.contains("mac")) return "MacOS";
        if (lower.contains("linux")) return "Linux";
        if (lower.contains("iphone") || lower.contains("ipad")) return "iOS";
        if (lower.contains("android")) return "Android";
        return platform;
    }

    private String groupScreenResolution(String screen) {
        if (!StringUtils.hasText(screen)) return "unknown";
        try {
            String[] parts = screen.split("x");
            if (parts.length == 2) {
                int width = Integer.parseInt(parts[0].trim());
                // 高度其实不太重要，主要看宽度来判断设备类型
                
                if (width >= 3840) return "4K";
                if (width >= 2560) return "2K";
                if (width >= 1920) return "FHD";
                if (width >= 1366) return "HD";
                if (width >= 1024) return "Tablet";
                return "Mobile";
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid screen resolution format: {}", screen);
        }
        return "unknown";
    }
}