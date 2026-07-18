package com.xuan.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.dto.DailyViewCountDTO;
import com.xuan.dto.ViewPageQueryDTO;
import com.xuan.entity.Views;
import com.xuan.mapper.ViewMapper;
import com.xuan.result.PageResult;
import com.xuan.service.IViewService;
import com.xuan.vo.ViewQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 浏览记录服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewServiceImpl extends ServiceImpl<ViewMapper, Views> implements IViewService {

    /**
     * 分页查询浏览记录
     * @param viewPageQueryDTO 查询参数
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(ViewPageQueryDTO viewPageQueryDTO) {
        //1.创建分页对象
        Page<Views> page = new Page<>(viewPageQueryDTO.getPage(), viewPageQueryDTO.getPageSize());
        //2.创建查询条件
        Page<Views> viewPage = page(page, buildQueryWrapper(viewPageQueryDTO));
        //3.转换为 QueryVO 并返回
        Page<ViewQueryVO> voPage = new Page<>(viewPage.getCurrent(), viewPage.getSize(), viewPage.getTotal());
        voPage.setRecords(viewPage.getRecords().stream()
                .map(view -> cn.hutool.core.bean.BeanUtil.copyProperties(view, ViewQueryVO.class))
                .toList());
        return PageResult.fromIPage(voPage);
    }

    /**
     * 批量删除浏览记录
     * @param ids 浏览记录 ID 列表
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        removeBatchByIds(ids);
    }

    /**
     * 统计浏览记录总数
     * @return 浏览记录总数
     */
    @Override
    public Integer countTotal() {
        return Math.toIntExact(count());
    }

    /**
     * 统计今日浏览量
     * @return 今日浏览量
     */
    @Override
    public Integer countToday() {
        //1. 获取当前时间
        LocalDate today = LocalDate.now();
        //2. 获取今天开始和结束的时间
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        //3. 查询
        LambdaQueryWrapper<Views> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(Views::getViewTime, startOfDay, endOfDay);
        //4. 返回结果
        return Math.toIntExact(count(wrapper));
    }

    /**
     * 获取指定日期范围内的浏览量
     * @param begin 开始时间
     * @param end 结束时间
     * @return 每日浏览量
     */
    @Override
    public List<DailyViewCountDTO> getDailyViewStats(LocalDate begin, LocalDate end) {
        return baseMapper.getDailyViewStats(begin, end);
    }

    //<==========私有辅助方法==============>

    /**
     * 构建查询条件
     * @param viewPageQueryDTO 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<Views> buildQueryWrapper(ViewPageQueryDTO viewPageQueryDTO) {
        //1.构建查询条件
        LambdaQueryWrapper<Views> wrapper = new LambdaQueryWrapper<>();
        //2.添加查询条件
        if (StrUtil.isNotBlank(viewPageQueryDTO.getPagePath())) {
            wrapper.like(Views::getPagePath, viewPageQueryDTO.getPagePath());
        }
        if (StrUtil.isNotBlank(viewPageQueryDTO.getReferer())) {
            wrapper.like(Views::getReferer, viewPageQueryDTO.getReferer());
        }
        if (viewPageQueryDTO.getVisitorId() != null) {
            wrapper.eq(Views::getVisitorId, viewPageQueryDTO.getVisitorId());
        }
        wrapper.orderByDesc(Views::getViewTime);
        //3.返回
        return wrapper;
    }
}
