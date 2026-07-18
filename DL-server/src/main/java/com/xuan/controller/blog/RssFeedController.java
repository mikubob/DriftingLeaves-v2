package com.xuan.controller.blog;


import com.xuan.service.IRssFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 博客端 RSS Feed 接口
 */
@Slf4j
@RestController("blogRssFeedController")
@RequestMapping("/blog")
@RequiredArgsConstructor
public class RssFeedController {

    private final IRssFeedService rssFeedService;

    /**
     * 生成 RSS 2.0 Feed XML
     */
    @GetMapping(value = "/rss", produces = "application/xml; charset=UTF-8")
    @Cacheable(value = "rssFeed", key = "'xml'")
    public String rssFeed() {
        String xml = rssFeedService.generateRssFeed();
        return xml;
    }
}
