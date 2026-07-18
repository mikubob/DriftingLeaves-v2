package com.xuan.service.impl;

import com.xuan.dto.ArticleTitleViewCountDTO;
import com.xuan.dto.DailyViewCountDTO;
import com.xuan.dto.ProvinceCountDTO;
import com.xuan.service.IArticleCategoryService;
import com.xuan.service.IArticleCommentService;
import com.xuan.service.IArticleService;
import com.xuan.service.IArticleTagService;
import com.xuan.service.IMessageService;
import com.xuan.service.IReportService;
import com.xuan.service.IViewService;
import com.xuan.service.IVisitorService;
import com.xuan.vo.AdminOverviewVO;
import com.xuan.vo.ArticleViewTop10VO;
import com.xuan.vo.BlogReportVO;
import com.xuan.vo.ProvinceVisitorVO;
import com.xuan.vo.ViewReportVO;
import com.xuan.vo.VisitorReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements IReportService {

    private final IViewService viewService;
    private final IVisitorService visitorService;
    private final IArticleCategoryService articleCategoryService;
    private final IArticleService articleService;
    private final IArticleTagService articleTagService;
    private final IArticleCommentService articleCommentService;
    private final IMessageService messageService;

    /**
     * 获取博客报表
     *
     * @return 博客报表
     */
    @Override
    @Cacheable(value = "blogReport", key = "'stats'")
    public BlogReportVO getBlogReport() {
        return BlogReportVO.builder()
                .viewTotalCount(viewService.countTotal())
                .viewTodayCount(viewService.countToday())
                .visitorTotalCount(visitorService.countTotal())
                .categoryTotalCount(articleCategoryService.countTotal())
                .articleTotalCount(articleService.countPublished())
                .tagTotalCount(articleTagService.countTotal())
                .build();
    }

    /**
     * 获取浏览量统计
     *
     * @param begin 开始时间
     * @param end   结束时间
     * @return 浏览量统计
     */
    @Override
    public ViewReportVO getViewStatistics(LocalDate begin, LocalDate end) {
        //1. 获取指定日期范围内的日期列表
        List<LocalDate> dateList = getDateList(begin, end);
        //2. 获取指定日期范围内的浏览量
        List<DailyViewCountDTO> dailyStats = viewService.getDailyViewStats(begin, end);
        //3. 转换为Map, key: 日期, value: 浏览量
        Map<LocalDate, Integer> dailyViewMap = dailyStats.stream()
                .collect(Collectors.toMap(DailyViewCountDTO::getDate, DailyViewCountDTO::getCount));
        //4. 将日期列表和浏览量列表转换为字符串
        List<Integer> viewCountList = dateList.stream()
                .map(date -> dailyViewMap.getOrDefault(date, 0))
                .toList();
        //5. 返回结果
        return ViewReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .viewCountList(viewCountList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 获取访客统计
     *
     * @param begin 起始日期
     * @param end   结束日期
     * @return 访客统计
     */
    @Override
    public VisitorReportVO getVisitorStatistics(LocalDate begin, LocalDate end) {
        //1. 获取指定日期范围内的日期列表
        List<LocalDate> dateList = getDateList(begin, end);
        //2. 获取指定日期范围内的访客量数据
        List<DailyViewCountDTO> dailyStats = visitorService.getDailyNewVisitorStats(begin, end);
        //3. 转换为Map结构，便于按日期快速查找访客量，key: 日期, value: 新增访客量
        Map<LocalDate, Integer> dailyNewVisitorMap = dailyStats.stream()
                .collect(Collectors.toMap(DailyViewCountDTO::getDate, DailyViewCountDTO::getCount));
        //4. 根据日期列表生成对应的新访客数量列表，不存在的日期默认为0
        List<Integer> newVisitorCountList = dateList.stream()
                .map(date -> dailyNewVisitorMap.getOrDefault(date, 0))
                .toList();
        //5. 计算累计访客量，通过累加每日新访客数量得到历史总访客量
        List<Integer> totalViewCountList = new ArrayList<>();
        for (int i = 0; i < newVisitorCountList.size(); i++) {
            if (i == 0) {
                totalViewCountList.add(newVisitorCountList.get(i));
            } else {
                totalViewCountList.add(newVisitorCountList.get(i) + totalViewCountList.get(i - 1));
            }
        }

        //6. 构建并返回访客统计结果，将各数据列表转换为逗号分隔的字符串格式
        return VisitorReportVO.builder()
                .dateList(dateList.stream().map(LocalDate::toString).collect(Collectors.joining(",")))
                .newVisitorCountList(newVisitorCountList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .totalVisitorCountList(totalViewCountList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 获取访客省份分布统计
     * @return 访客省份分布统计
     */
    @Override
    public ProvinceVisitorVO getProvinceDistribution() {
        //1. 获取访客省份分布
        List<ProvinceCountDTO> provinceStats = visitorService.getProvinceDistribution();
        //2. 提取省份对应的访客数量列表
        List<Integer> countList = provinceStats.stream()
                .map(ProvinceCountDTO::getCount)
                .toList();
        //3. 提取省份列表
        List<String> provinceList = provinceStats.stream()
                .map(ProvinceCountDTO::getProvince)
                .toList();
        //4. 返回结果
        return ProvinceVisitorVO.builder()
                .provinceList(String.join(",", provinceList))
                .countList(countList.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .build();
    }

    /**
     * 获取文章访问量排行前十
     * @return 文章访问量排行前十
     */
    @Override
    public ArticleViewTop10VO getArticleViewTop10() {
        // 1. 获取文章访问量排行前十的列表
        List<ArticleTitleViewCountDTO> top10List = articleService.getViewTop10();
        // 2. 提取标题列表
        List<String> titleList = top10List.stream()
                .map(ArticleTitleViewCountDTO::getTitle)
                .toList();
        // 3. 提取浏览量列表
        List<Integer> viewCountList = top10List.stream()
                .map(ArticleTitleViewCountDTO::getViewCount)
                .toList();
        // 4. 返回结果
        return ArticleViewTop10VO.builder()
                .titleList(titleList)
                .viewCountList(viewCountList)
                .build();
    }

    /**
     * 获取管理端总览数据
     * @return 管理端总览数据
     */
    @Override
    public AdminOverviewVO getAdminOverview() {
        return AdminOverviewVO.builder()
                .totalViewCount(viewService.countTotal())
                .totalVisitorCount(visitorService.countTotal())
                .todayViewCount(viewService.countToday())
                .todayNewVisitorCount(visitorService.countToday())
                .totalArticleCount(articleService.countPublished())
                .totalCommentCount(articleCommentService.countTotal())
                .totalMessageCount(messageService.countTotal())
                .pendingCommentCount(articleCommentService.countPending())
                .pendingMessageCount(messageService.countPending())
                .build();
    }

    /**
     * 获取指定日期范围内的日期列表
     */
    private List<LocalDate> getDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        return dateList;
    }
}
