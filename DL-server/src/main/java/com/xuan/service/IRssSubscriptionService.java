package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.RssSubscriptionDTO;
import com.xuan.dto.RssSubscriptionPageQueryDTO;
import com.xuan.entity.RssSubscriptions;
import com.xuan.result.PageResult;
import com.xuan.vo.RssSubscriptionStatusVO;

import java.util.List;

public interface IRssSubscriptionService extends IService<RssSubscriptions> {
    /**
     * 添加RSS订阅
     * @param rssSubscriptionDTO
     */
    void addSubscription(RssSubscriptionDTO rssSubscriptionDTO);

    /**
     * 分页查询RSS订阅列表
     * @param rssSubscriptionPageQueryDTO
     * @return
     */
    PageResult pageQuery(RssSubscriptionPageQueryDTO rssSubscriptionPageQueryDTO);

    /**
     * 更新RSS订阅
     * @param rssSubscriptions
     */
    void updateSubscription(RssSubscriptions rssSubscriptions);

    /**
     * 批量删除RSS订阅
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 根据ID查询RSS订阅
     * @param id
     * @return
     */
    RssSubscriptions getRssWithId(Long id);

    /**
     * 获取所有激活的订阅
     * @return
     */
    List<RssSubscriptions> getAllActiveSubscriptions();

    /**
     * 根据邮箱取消订阅
     * @param email
     */
    void unsubscribeByEmail(String email);

    /**
     * 检查用户是否已订阅
     * @param userId
     * @return
     */
    boolean hasSubscribed(Long userId);

    /**
     * 获取用户订阅详情
     * @param userId
     * @return
     */
    RssSubscriptionStatusVO getSubscriptionStatus(Long userId);
}
