 package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.DailyViewCountDTO;
import com.xuan.dto.ProvinceCountDTO;
import com.xuan.dto.VisitorPageQueryDTO;
import com.xuan.dto.VisitorRecordDTO;
import com.xuan.entity.Visitors;
import com.xuan.result.PageResult;
import com.xuan.vo.VisitorRecordVO;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;

public interface  IVisitorService extends IService<Visitors> {
    /**
     * 记录访客访问信息
     * @param visitorRecordDTO
     * @param httpRequest
     * @return
     */
    VisitorRecordVO recordVisitorViewInfo(VisitorRecordDTO visitorRecordDTO, HttpServletRequest httpRequest);

    /**
     * 分页查询访客列表
     * @param visitorPageQueryDTO
     * @return
     */
    PageResult pageQuery(VisitorPageQueryDTO visitorPageQueryDTO);

    /**
     * 批量封禁访客
     * @param ids
     */
    void batchBlock(List<Long> ids);

    /**
     * 批量解封访客
     * @param ids
     */
    void batchUnblock(List<Long> ids);

    /**
     * 根据指纹查询访客
     * @param fingerprint 指纹
     */
    Visitors findVisitorByFingerprint(String fingerprint);

    /**
     * 统计总访客数
     * @return 总访客数
     */
    Integer countTotal();

    /**
     * 统计今日新增访客数
     * @return 今日新增访客数
     */
    Integer countToday();

    /**
     * 统计今日访客数
     * @param begin 起始时间
     * @param end 结束时间
     * @return 访客数
     */
    List<DailyViewCountDTO> getDailyNewVisitorStats(LocalDate begin, LocalDate end);

    /**
     * 获取访客省份分布
     * @return 访客省份分布
     */
    List<ProvinceCountDTO> getProvinceDistribution();

    /**
     * 批量删除访客
     * @param ids 访客ID列表
     */
    void batchDeleteVisitors(List<Long> ids);
}
