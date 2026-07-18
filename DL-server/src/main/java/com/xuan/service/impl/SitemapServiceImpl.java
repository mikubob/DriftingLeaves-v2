package com.xuan.service.impl;

import com.xuan.properties.WebsiteProperties;
import com.xuan.result.PageResult;
import com.xuan.service.IArticleService;
import com.xuan.service.SitemapService;
import com.xuan.vo.BlogArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sitemap 服务实现类
 * 生成符合 Sitemap 0.9 协议的 XML 文件，用于搜索引擎优化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SitemapServiceImpl implements SitemapService {

    private final IArticleService articleService;
    private final WebsiteProperties websiteProperties;

    /**
     * W3C 日期时间格式，符合 Sitemap 协议要求
     * 格式：yyyy-MM-dd'T'HH:mm:ss+08:00（带时区信息）
     */
    private static final DateTimeFormatter W3C_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+08:00");

    /**
     * 生成 Sitemap XML
     * 包含首页、文章页面、归档页、友链页、留言板
     *
     * @return Sitemap XML 字符串
     */
    @Override
    public String generateSitemap() {
        String baseUrl = websiteProperties.getBlog();

        // 确保 baseUrl 末尾没有斜杠，防止拼接时出现双斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 获取已发布文章列表，最多500篇
        // 生产环境若文章数量超过500，需考虑分页生成多个 sitemap 文件
        PageResult<BlogArticleVO> pageResult = articleService.getPublishedPage(1, 500);
        List<BlogArticleVO> articles = pageResult.getRecords();

        // 预分配 StringBuilder 容量：预估每条记录约200字符 * 500条 + 头部尾部
        StringBuilder xml = new StringBuilder(100000);

        // XML 声明和命名空间
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 1. 首页 - 最高优先级，更新频率最高
        appendUrlEntry(xml, baseUrl, null, "daily", "1.0");

        // 2. 文章列表页
        if (articles != null && !articles.isEmpty()) {
            for (BlogArticleVO article : articles) {
                // 跳过 slug 为空或无效的文章
                if (!StringUtils.hasText(article.getSlug())) {
                    log.warn("跳过无效文章，slug为空，文章ID: {}", article.getId());
                    continue;
                }

                // 构建文章 URL，对 slug 进行 XML 转义防止特殊字符破坏结构
                String loc = baseUrl + "/article/" + escapeXml(article.getSlug());

                // 格式化发布时间作为 lastmod
                String lastMod = null;
                if (article.getPublishTime() != null) {
                    lastMod = article.getPublishTime().format(W3C_DATE_FMT);
                }

                // 文章页面优先级较高，更新频率为每周
                appendUrlEntry(xml, loc, lastMod, "weekly", "0.8");
            }
        }

        // 3. 静态页面（归档、友链、留言板）
        appendUrlEntry(xml, baseUrl + "/archive", null, "weekly", "0.6");
        appendUrlEntry(xml, baseUrl + "/friends", null, "monthly", "0.5");
        appendUrlEntry(xml, baseUrl + "/message", null, "daily", "0.5");

        xml.append("</urlset>\n");

        return xml.toString();
    }

    /**
     * 构建单个 URL 条目
     *
     * @param xml        StringBuilder 对象
     * @param loc        URL 地址
     * @param lastMod    最后修改时间（可为空）
     * @param changefreq 更新频率（always/hourly/daily/weekly/monthly/yearly/never）
     * @param priority   优先级（0.0-1.0）
     */
    private void appendUrlEntry(StringBuilder xml, String loc, String lastMod, String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(loc).append("</loc>\n");
        if (lastMod != null) {
            xml.append("    <lastmod>").append(lastMod).append("</lastmod>\n");
        }
        // changefreq 和 priority 标签虽被 Google 忽略，但保留以兼容其他搜索引擎
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    /**
     * XML 特殊字符转义
     * 防止 & < > " ' 等字符破坏 XML 文档结构
     *
     * @param input 原始字符串
     * @return 转义后的安全字符串
     */
    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
