package com.xuan.service;


import com.xuan.vo.AdminOverviewVO;
import com.xuan.vo.ArticleViewTop10VO;
import com.xuan.vo.BlogReportVO;
import com.xuan.vo.ProvinceVisitorVO;
import com.xuan.vo.ViewReportVO;
import com.xuan.vo.VisitorReportVO;

import java.time.LocalDate;

public interface IReportService {

    /**
     * 获取博客统计数据
     */
    BlogReportVO getBlogReport();

    /**
     * 浏览量统计
     */
    ViewReportVO getViewStatistics(LocalDate begin, LocalDate end);

    /**
     * 访客统计
     */
    VisitorReportVO getVisitorStatistics(LocalDate begin, LocalDate end);

    /**
     * 访客省份分布统计
     */
    ProvinceVisitorVO getProvinceDistribution();

    /**
     * 文章访问量排行前十
     */
    ArticleViewTop10VO getArticleViewTop10();

    /**
     * 获取管理端总览数据
     */
    AdminOverviewVO getAdminOverview();
}
