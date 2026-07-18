package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.RssSubscriptionPageQueryDTO;
import com.xuan.entity.RssSubscriptions;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IRssSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端 RSS 订阅接口
 */
@Slf4j
@RestController("adminRssSubscriptionController")
@RequestMapping("/admin/rssSubscription")
@RequiredArgsConstructor
public class RssSubscriptionController {

    private final IRssSubscriptionService rssSubscriptionService;

    /**
     * 分页查询RSS订阅列表
     * @param rssSubscriptionPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> getSubscriptionList(RssSubscriptionPageQueryDTO rssSubscriptionPageQueryDTO) {
        log.info("获取RSS订阅列表,{}", rssSubscriptionPageQueryDTO);
        PageResult pageResult = rssSubscriptionService.pageQuery(rssSubscriptionPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 获取所有激活的订阅
     * @return
     */
    @GetMapping
    public Result<List<RssSubscriptions>> getAllActiveSubscriptions() {
        List<RssSubscriptions> rssSubscriptionsList = rssSubscriptionService.getAllActiveSubscriptions();
        return Result.success(rssSubscriptionsList);
    }

    /**
     * 根据ID查询RSS订阅
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<RssSubscriptions> getById(@PathVariable Long id) {
        log.info("根据ID查询RSS订阅,{}", id);
        RssSubscriptions rssSubscriptions = rssSubscriptionService.getRssWithId(id);
        return Result.success(rssSubscriptions);
    }

    /**
     * 更新RSS订阅
     * @param rssSubscriptions
     * @return
     */
    @PutMapping
    @OperationLog(value = OperationType.UPDATE, target = "rssSubscription", targetId = "#rssSubscriptions.id")
    public Result updateSubscription(@RequestBody RssSubscriptions rssSubscriptions) {
        log.info("更新RSS订阅,{}", rssSubscriptions);
        rssSubscriptionService.updateSubscription(rssSubscriptions);
        return Result.success();
    }

    /**
     * 批量删除RSS订阅
     * @param ids
     * @return
     */
    @DeleteMapping
    @OperationLog(value = OperationType.DELETE, target = "rssSubscription", targetId = "#ids")
    public Result deleteSubscription(@RequestParam List<Long> ids) {
        log.info("批量删除RSS订阅,{}", ids);
        rssSubscriptionService.batchDelete(ids);
        return Result.success();
    }
}
