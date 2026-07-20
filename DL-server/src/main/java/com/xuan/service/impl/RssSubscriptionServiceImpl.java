package com.xuan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.MessageConstant;
import com.xuan.dto.RssSubscriptionDTO;
import com.xuan.dto.RssSubscriptionPageQueryDTO;
import com.xuan.entity.RssSubscriptions;
import com.xuan.exception.RssSubscriptionException;
import com.xuan.mapper.RssSubscriptionMapper;
import com.xuan.result.PageResult;
import com.xuan.service.IRssSubscriptionService;
import com.xuan.vo.RssSubscriptionQueryVO;
import com.xuan.vo.RssSubscriptionStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RSS 订阅服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RssSubscriptionServiceImpl extends ServiceImpl<RssSubscriptionMapper, RssSubscriptions> implements IRssSubscriptionService {

    /**
     * 添加 RSS 订阅
     * @param rssSubscriptionDTO 订阅数据
     * @param userId 当前登录用户 ID（由 Controller 从 SecurityContext 取出）
     */
    @Override
    public void addSubscription(RssSubscriptionDTO rssSubscriptionDTO, Long userId) {
        // 检查邮箱是否已存在
        LambdaQueryWrapper<RssSubscriptions> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RssSubscriptions::getEmail, rssSubscriptionDTO.getEmail());
        RssSubscriptions existingSubscription = getOne(wrapper);

        if (existingSubscription != null) {
            // 如果已存在且激活，抛出异常
            if (existingSubscription.getIsActive() == 1) {
                throw new RssSubscriptionException(MessageConstant.RSS_ALREADY_EXISTS);
            }
            // 如果已存在但未激活，重新激活
            existingSubscription.setIsActive(1);
            existingSubscription.setNickname(rssSubscriptionDTO.getNickname());
            existingSubscription.setUnSubscribeTime(null);
            updateById(existingSubscription);
        } else {
            // 新增订阅
            RssSubscriptions rssSubscriptions = RssSubscriptions.builder()
                    .userId(userId)
                    .nickname(rssSubscriptionDTO.getNickname())
                    .email(rssSubscriptionDTO.getEmail())
                    .isActive(1)
                    .subscribeTime(LocalDateTime.now())
                    .build();
            save(rssSubscriptions);
        }
    }

    /**
     * 分页查询 RSS 订阅
     * @param rssSubscriptionPageQueryDTO 查询参数
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(RssSubscriptionPageQueryDTO rssSubscriptionPageQueryDTO) {
        // 构建分页条件
        Page<RssSubscriptions> page = new Page<>(rssSubscriptionPageQueryDTO.getPage(), rssSubscriptionPageQueryDTO.getPageSize());

        // 构建查询条件
        LambdaQueryWrapper<RssSubscriptions> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(rssSubscriptionPageQueryDTO.getEmail() != null && !rssSubscriptionPageQueryDTO.getEmail().isEmpty(),
                RssSubscriptions::getEmail, rssSubscriptionPageQueryDTO.getEmail())
                .eq(rssSubscriptionPageQueryDTO.getIsActive() != null,
                        RssSubscriptions::getIsActive, rssSubscriptionPageQueryDTO.getIsActive())
                .orderByDesc(RssSubscriptions::getSubscribeTime);

        // 执行分页查询
        Page<RssSubscriptions> result = page(page, wrapper);

        // 转换为 QueryVO 并返回
        Page<RssSubscriptionQueryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(sub -> cn.hutool.core.bean.BeanUtil.copyProperties(sub, RssSubscriptionQueryVO.class))
                .toList());
        return PageResult.fromIPage(voPage);
    }

    /**
     * 更新 RSS 订阅
     * @param rssSubscriptions 更新的订阅数据
     */
    @Override
    public void updateSubscription(RssSubscriptions rssSubscriptions) {
        updateById(rssSubscriptions);
    }

    /**
     * 批量删除 RSS 订阅
     * @param ids 订阅 ID 列表
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        removeBatchByIds(ids);
    }

    /**
     * 根据 ID 查询 RSS 订阅
     * @param id 订阅 ID
     * @return 订阅数据
     */
    @Override
    public RssSubscriptions getRssWithId(Long id) {
        return super.getById(id);
    }

    /**
     * 获取所有激活的订阅
     * @return 激活的订阅列表
     */
    @Override
    public List<RssSubscriptions> getAllActiveSubscriptions() {
        LambdaQueryWrapper<RssSubscriptions> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RssSubscriptions::getIsActive, 1)
                .orderByDesc(RssSubscriptions::getSubscribeTime);
        return list(wrapper);
    }

    /**
     * 根据邮箱取消订阅
     * @param email 邮箱
     */
    @Override
    public void unsubscribeByEmail(String email) {
        // 根据邮箱查询订阅
        LambdaQueryWrapper<RssSubscriptions> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RssSubscriptions::getEmail, email);
        RssSubscriptions subscription = getOne(wrapper);

        // 订阅不存在
        if (subscription == null) {
            throw new RssSubscriptionException(MessageConstant.RSS_NOT_FOUND);
        }

        // 已经取消订阅
        if (subscription.getIsActive() == 0) {
            throw new RssSubscriptionException(MessageConstant.RSS_NOT_FOUND);
        }

        // 更新订阅状态
        subscription.setIsActive(0);
        subscription.setUnSubscribeTime(LocalDateTime.now());
        updateById(subscription);
    }

    /**
     * 获取用户订阅详情
     * @param userId 用户 ID
     * @return 订阅详情
     */
    @Override
    public RssSubscriptionStatusVO getSubscriptionStatus(Long userId) {
        // 用户 ID 为空，返回未订阅状态
        if (userId == null) {
            return RssSubscriptionStatusVO.builder().subscribed(false).build();
        }

        // 查询用户的激活订阅
        RssSubscriptions sub = lambdaQuery()
                .eq(RssSubscriptions::getUserId, userId)
                .eq(RssSubscriptions::getIsActive, 1)
                .one();

        // 未找到订阅记录，返回未订阅状态
        if (sub == null) {
            return RssSubscriptionStatusVO.builder().subscribed(false).build();
        }

        // 返回订阅详情
        return RssSubscriptionStatusVO.builder()
                .subscribed(true)
                .nickname(sub.getNickname())
                .email(sub.getEmail())
                .build();
    }
}
