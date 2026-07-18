package com.xuan.service.impl;

import com.xuan.properties.WebsiteProperties;
import com.xuan.result.PageResult;
import com.xuan.service.IArticleService;
import com.xuan.service.IPersonalInfoService;
import com.xuan.service.IRssFeedService;
import com.xuan.vo.BlogArticleVO;
import com.xuan.vo.PersonalInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RssFeedServiceImpl implements IRssFeedService {

    private final IArticleService articleService;
    private final IPersonalInfoService personalInfoService;
    private final WebsiteProperties websiteProperties;

    private static final DateTimeFormatter RSS_DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss '+0800'", java.util.Locale.ENGLISH);

    /**
     * 生成 RSS 2.0 Feed XML
     */
    public String generateRssFeed() {
        String BLOG_BASE_URL = websiteProperties.getBlog();

        // 获取个人信息作为站点信息
        PersonalInfoVO info = personalInfoService.getPersonalInfo();
        String siteName = info != null && info.getNickname() != null ? info.getNickname() + "的博客" : "DriftingLeaves Blog";
        String siteDescription = info != null && info.getDescription() != null ? info.getDescription() : "个人博客";

        // 获取最新20篇已发布文章
        PageResult<BlogArticleVO> pageResult = articleService.getPublishedPage(1, 20);
        List<BlogArticleVO> articles = pageResult.getRecords();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n");
        xml.append("  <channel>\n");
        xml.append("    <title>").append(escapeXml(siteName)).append("</title>\n");
        xml.append("    <link>").append(BLOG_BASE_URL).append("</link>\n");
        xml.append("    <description>").append(escapeXml(siteDescription)).append("</description>\n");
        xml.append("    <language>zh-CN</language>\n");
        xml.append("    <lastBuildDate>").append(LocalDateTime.now().format(RSS_DATE_FMT)).append("</lastBuildDate>\n");
        xml.append("    <atom:link href=\"").append(BLOG_BASE_URL).append("/rss\" rel=\"self\" type=\"application/rss+xml\"/>\n");

        if (articles != null) {
            for (BlogArticleVO article : articles) {
                xml.append("    <item>\n");
                xml.append("      <title>").append(escapeXml(article.getTitle())).append("</title>\n");
                xml.append("      <link>").append(BLOG_BASE_URL).append("/article/").append(article.getSlug()).append("</link>\n");
                xml.append("      <guid isPermaLink=\"true\">").append(BLOG_BASE_URL).append("/article/").append(article.getSlug()).append("</guid>\n");
                if (article.getSummary() != null) {
                    xml.append("      <description>").append(escapeXml(article.getSummary())).append("</description>\n");
                }
                if (article.getCategoryName() != null) {
                    xml.append("      <category>").append(escapeXml(article.getCategoryName())).append("</category>\n");
                }
                if (article.getPublishTime() != null) {
                    xml.append("      <pubDate>").append(article.getPublishTime().format(RSS_DATE_FMT)).append("</pubDate>\n");
                }
                xml.append("    </item>\n");
            }
        }

        xml.append("  </channel>\n");
        xml.append("</rss>\n");
        return xml.toString();
    }

    /**
     * XML特殊字符转义
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
