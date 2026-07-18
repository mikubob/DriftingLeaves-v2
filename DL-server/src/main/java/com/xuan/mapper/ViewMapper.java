package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.dto.DailyViewCountDTO;
import com.xuan.entity.Views;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ViewMapper extends BaseMapper<Views> {

    List<DailyViewCountDTO> getDailyViewStats(@Param("begin") LocalDate begin, @Param("end") LocalDate end);
}
