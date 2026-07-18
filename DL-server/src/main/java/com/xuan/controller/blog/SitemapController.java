package com.xuan.controller.blog;

import com.xuan.service.SitemapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 博客端 Sitemap 接口
 */
@Slf4j
@RestController("blogSitemapController")
@RequestMapping("/blog")
@RequiredArgsConstructor
public class SitemapController {

    private final SitemapService sitemapService;


    /**
     * 动态生成站点地图 XML
     */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml; charset=UTF-8")
    @Cacheable(value = "sitemap", key = "'xml'")
    public String sitemap() {
        return sitemapService.generateSitemap();
    }
}
