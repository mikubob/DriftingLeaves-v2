package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IReportService;
import com.xuan.vo.BlogReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端统计相关接口
 */
@Slf4j
@RestController("blogReportController")
@RequestMapping("/blog/report")
@RequiredArgsConstructor
public class ReportController {

    private final IReportService reportService;

    /**
     * 获取博客统计数据
     */
    @GetMapping
    public Result<BlogReportVO> getBlogReport() {
        log.info("博客端获取统计数据");
        BlogReportVO blogReportVO = reportService.getBlogReport();
        return Result.success(blogReportVO);
    }
}
