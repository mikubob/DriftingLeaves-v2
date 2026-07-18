package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.DailyViewCountDTO;
import com.xuan.dto.ViewPageQueryDTO;
import com.xuan.entity.Views;
import com.xuan.result.PageResult;

import java.time.LocalDate;
import java.util.List;

public interface IViewService extends IService<Views> {
    /**
     * 分页查询浏览记录
     * @param viewPageQueryDTO
     * @return
     */
    PageResult pageQuery(ViewPageQueryDTO viewPageQueryDTO);

    /**
     * 批量删除浏览记录
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 统计总浏览量
     * @return 总浏览量
     */
    Integer countTotal();

    /**
     * 统计今日浏览量
     * @return 今日浏览量
     */
    Integer countToday();

    /**
     * 获取指定日期范围内的浏览量
     * @param begin 开始时间
     * @param end 结束时间
     * @return 每日浏览量
     */
    List<DailyViewCountDTO> getDailyViewStats(LocalDate begin, LocalDate end);
}
