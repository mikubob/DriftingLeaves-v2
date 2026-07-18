package com.xuan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * 【安全扫描日志过滤器配置类】
 *
 * 核心功能：
 * 1. 拦截常见的自动化漏洞扫描、敏感文件探测请求（如 .env, .git, wp-admin 等）。
 * 2. 对恶意请求直接返回 HTTP 404 (Not Found)，避免暴露服务器真实信息（如 403 Forbidden 会暴露路径存在）。
 * 3. 将拦截记录写入独立的日志文件（通过 Logger 名称 "SECURITY_SCAN" 区分），防止污染业务主日志。
 * 4. 对合法请求正常放行，并自动将客户端 IP 注入 MDC (Mapped Diagnostic Context)，便于后续业务日志追踪来源。
 *
 * @author Xuan
 */
@Configuration
public class SecurityLogConfig {

    /**
     * 定义独立的日志记录器。
     * 名称为 "SECURITY_SCAN"。
     *
     * 【配合 Logback 配置】：
     * 需在 logback-spring.xml 中配置一个专门的 Appender (如 security-scan.log)，
     * 并将 logger name="SECURITY_SCAN" 指向该 Appender，且设置 additivity="false"。
     * 这样扫描日志就不会混入 info.log 或 error.log 中。
     */
    public static final Logger SECURITY_SCAN_LOG = LoggerFactory.getLogger("SECURITY_SCAN");

    /**
     * 【白名单】项目自身的合法路径前缀。
     * 逻辑：只要请求路径以这些前缀开头，直接视为合法，跳过后续的扫描检测逻辑。
     *
     * ⚠️ 重要提示：
     * 如果你的项目使用了 Spring Actuator (监控) 或 Alibaba Druid (数据库监控)，
     * 务必将 "/actuator/" 和 "/druid/" 加入此列表，否则监控页面会被误拦截！
     */
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/blog/",      // 博客模块
            "/admin/",     // 管理后台
            "/home/",      // 首页相关
            "/cv/",        // 简历页面
            "/health",     // 健康检查接口 (通常用于 K8s 探针)
            "/ws",         // WebSocket 路径
            "/actuator/",  // [建议添加] Spring Boot 监控端点
            "/druid/"      // [建议添加] Druid 监控控制台
    );

    /**
     * 【黑名单 - 后缀】典型扫描路径中出现的敏感文件后缀。
     * 攻击者常尝试下载配置文件、备份文件或数据库文件。
     */
    private static final Set<String> SCAN_EXTENSIONS = Set.of(
            ".env",         // 环境变量文件 (极高危)
            ".php",         // 尝试探测 PHP 环境
            ".bak", ".old", ".save", // 备份文件
            ".swp",         // Vim 编辑临时文件
            ".config", ".ini", ".properties", // 配置文件
            ".yml", ".yaml", // Kubernetes 或 Spring 配置
            ".sql",         // 数据库脚本
            ".tar.gz", ".zip", ".rar", // 压缩包
            ".git", ".svn"  // 版本控制目录 (虽通常是目录，但也常作为后缀出现在 URL 末尾)
    );

    /**
     * 【黑名单 - 路径片段】典型扫描路径中包含的敏感关键词。
     * 涵盖常见 CMS (WordPress)、中间件后台、云凭证目录等。
     */
    private static final Set<String> SCAN_SEGMENTS = Set.of(
            // --- 版本控制与敏感配置 ---
            "/.git", "/.svn", "/.htaccess", "/.htpasswd", "/.env",
            "/.aws", "/.docker",

            // --- WordPress 常见路径 ---
            "/wp-admin", "/wp-login", "/wp-content", "/wp-includes", "/wordpress",

            // --- 常见中间件/工具后台 ---
            "/phpmyadmin", "/phpinfo", "/info.php", "/config.php",
            "/debug", "/console",
            "/manager/html", // Tomcat Manager
            "/solr",         // Solr 搜索
            "/jenkins",      // Jenkins CI/CD
            "/struts",       // Struts2 漏洞探测
            "/cgi-bin"   // 传统 CGI 脚本

            // --- 注意：/actuator 和 /druid 如果没在白名单，建议在这里注释掉或确保白名单优先
            // "/actuator",   // 如果项目没用 actuator，可开启此项
            // "/druid"       // 如果项目没用 druid，可开启此项
    );

    /**
     * 注册过滤器 Bean。
     * 使用 OncePerRequestFilter 确保在一次请求中只执行一次过滤逻辑。
     */
    @Bean
    public OncePerRequestFilter securityScanLogFilter() {
        return new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain)
                    throws IOException, ServletException {

                // 1. 获取请求基本信息
                String path = request.getRequestURI(); // 获取 URI 路径 (不包含查询参数)
                String ip = getClientIp(request);      // 获取客户端真实 IP (处理代理情况)

                // ---------------------------------------------------------
                // 第一步：白名单校验
                // 如果路径属于项目合法业务，直接放行，不进行任何扫描检测。
                // 这能有效避免“误杀”自己的正常业务接口。
                // ---------------------------------------------------------
                if (isAllowedPath(path)) {
                    // 将 IP 放入 MDC 上下文，这样后续的业务日志（Info/Error）会自动带上 ip 字段
                    org.slf4j.MDC.put("ip", ip);
                    try {
                        chain.doFilter(request, response); // 放行请求
                    } finally {
                        // 请求结束后必须清理 MDC，防止线程复用导致的数据污染
                        org.slf4j.MDC.remove("ip");
                    }
                    return; // 结束处理
                }

                // ---------------------------------------------------------
                // 第二步：黑名单扫描检测
                // 如果不是白名单路径，则检查是否命中常见的扫描特征。
                // ---------------------------------------------------------
                if (isScanningRequest(path)) {
                    // 记录警告日志到独立文件
                    // 格式：SCAN_ATTEMPT: GET /.env from 192.168.1.5 UA: Mozilla/5.0...
                    SECURITY_SCAN_LOG.warn("SCAN_ATTEMPT: {} {} from {} UA: {}",
                            request.getMethod(),
                            path,
                            ip,
                            request.getHeader("User-Agent"));

                    // 【安全策略】直接返回 404 Not Found
                    // 为什么不是 403？
                    // 403 (Forbidden) 会告诉攻击者：“路径存在，但你无权访问”。这证实了目标存在。
                    // 404 (Not Found) 会让攻击者认为：“这个路径根本不存在”，增加其判断难度。
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.setContentType("text/plain");
                    response.getWriter().write("Not Found");
                    response.flushBuffer(); // 强制立即发送响应，终止后续处理
                    return;
                }

                // ---------------------------------------------------------
                // 第三步：普通请求放行
                // 既不在白名单（可能是新开发的未配置路径），也不命中黑名单，视为普通未知路径。
                // 交给 Spring MVC 或其他后续过滤器处理（通常会由 Spring 返回标准的 404）。
                // ---------------------------------------------------------
                org.slf4j.MDC.put("ip", ip);
                try {
                    chain.doFilter(request, response);
                } finally {
                    org.slf4j.MDC.remove("ip");
                }
            }

            /**
             * 判断路径是否在白名单内。
             *
             * @param path 请求 URI
             * @return true 如果是合法路径，false 否则
             */
            private boolean isAllowedPath(String path) {
                // 根路径通常也是合法的
                if ("/".equals(path)) {
                    return true;
                }
                // 遍历前缀集合，使用前缀匹配 (startsWith)
                // 性能优化点：如果前缀很多，可考虑改为 Trie 树，但目前 Set 遍历开销很小
                for (String prefix : ALLOWED_PREFIXES) {
                    if (path.startsWith(prefix)) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * 判断请求是否为扫描攻击。
             *
             * @param path 请求 URI
             * @return true 如果是扫描请求，false 否则
             */
            private boolean isScanningRequest(String path) {
                // 统一转为小写，防止大小写绕过 (如 /.GIT, /.Env)
                String lower = path.toLowerCase();

                // --- 优化点 1：后缀匹配 ---
                // 原代码是遍历所有后缀调用 endsWith，效率略低。
                // 优化：直接提取最后一个 '.' 之后的内容，去 Set 中查找 (O(1))。
                int lastDotIndex = lower.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    // 确保 '.' 不是在第一位 (防止 /.env 这种隐藏文件被漏掉，虽然 contains 也能抓到)
                    // 这里提取包括 '.' 的后缀，例如 ".env", ".git"
                    // 注意：对于 /.git 这种，lastIndexOf('.') 可能不是我们想要的，
                    // 但我们的 SCAN_EXTENSIONS 里包含了 ".git", ".svn" 等带点的，
                    // 而 /.git 路径本身不以 .git 结尾，所以主要靠下面的 contains 捕获。
                    // 此优化主要针对像 /config.properties.bak 这样的文件。

                    // 更严谨的后缀提取逻辑：
                    // 如果路径是 /aaa/.env，lastDotIndex 在 .env 处，提取 .env -> 命中
                    String ext = lower.substring(lastDotIndex);
                    if (SCAN_EXTENSIONS.contains(ext)) {
                        return true;
                    }
                }

                // 补充：针对以 "." 开头的隐藏文件/目录 (如 /.git, /.env)
                // 上面的后缀提取可能对 /.git (无后缀名，本身就是目录) 处理不够直观，
                // 但 SCAN_EXTENSIONS 里有 ".git"，如果 URL 是 /project/.git 则上面能抓到。
                // 如果 URL 是 /.git/ 或 /.git/config，上面抓不到，靠下面的 contains 抓。

                // --- 逻辑点 2：路径片段匹配 ---
                // 遍历敏感关键词，使用 contains 进行模糊匹配。
                // 能覆盖：/wp-admin/login.php, /aaa/.env, /test/phpmyadmin/ 等情况。
                for (String seg : SCAN_SEGMENTS) {
                    if (lower.contains(seg)) {
                        return true;
                    }
                }

                return false;
            }

            /**
             * 获取客户端真实 IP 地址。
             * 处理经过 Nginx、负载均衡器或多层代理的情况。
             *
             * 优先级：
             * 1. X-Forwarded-For (第一个 IP)
             * 2. X-Real-IP
             * 3. request.getRemoteAddr() (直连 IP)
             */
            private String getClientIp(HttpServletRequest request) {
                // 1. 尝试从 X-Forwarded-For 获取
                // 格式通常为: client, proxy1, proxy2
                String ip = request.getHeader("X-Forwarded-For");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    // 取逗号分隔的第一个 IP，并去除空格
                    return ip.split(",")[0].trim();
                }

                // 2. 尝试从 X-Real-IP 获取 (Nginx 常用)
                ip = request.getHeader("X-Real-IP");
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }

                // 3. 降级获取远程地址
                return request.getRemoteAddr();
            }
        };
    }
}