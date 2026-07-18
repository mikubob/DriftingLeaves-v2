package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.ExperienceDTO;
import com.xuan.entity.Experiences;
import com.xuan.mapper.ExperienceMapper;
import com.xuan.service.IExperienceService;
import com.xuan.service.IFriendLinkService;
import com.xuan.vo.ExperienceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 经历服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl extends ServiceImpl<ExperienceMapper, Experiences> implements IExperienceService {

    /**
     * 获取经历信息
     * @param type 经验类型
     * @return 经历信息
     */
    @Cacheable(value = "experiences", key = "'type_' + #type")
    @Override
    public List<Experiences> getExperience(Integer type) {
        //1.构建查询条件
        LambdaQueryWrapper<Experiences> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(Experiences::getType, type);
        }
        wrapper.orderByDesc(Experiences::getStartDate);

        //2.查询经历信息列表并且返回
        return baseMapper.selectList(wrapper);
    }

    /**
     * 添加经历信息
     * @param experienceDTO 经验信息
     */
    @Override
    @CacheEvict(value = "experiences", allEntries = true)
    public void addExperience(ExperienceDTO experienceDTO) {
        //1.创建实体对象
        Experiences experiences = BeanUtil.copyProperties(experienceDTO, Experiences.class);
        //2.添加新的经历信息
        save(experiences);
    }

    /**
     * 修改经历信息
     * @param experienceDTO 经验信息
     */
    @Override
    @CacheEvict(value = "experiences", allEntries = true)
    public void updateExperience(ExperienceDTO experienceDTO) {
        //1.创建实体对象
        Experiences experiences = BeanUtil.copyProperties(experienceDTO, Experiences.class);
        //2.修改经历信息
        updateById(experiences);
    }

    /**
     * 批量删除
     * @param ids 经验id列表
     */
    @Override
    @CacheEvict(value = "experiences", allEntries = true)
    public void batchDelete(List<Long> ids) {
        removeByIds(ids);
    }

    /**
     * 获取全部
     * @return 全部
     */
    @Override
    @Cacheable(value = "experiences", key = "'all'")
    public List<ExperienceVO> getAllExperience() {
        //1.构建查询条件
        LambdaQueryWrapper<Experiences> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(Experiences::getIsVisible, StatusConstant.ENABLE);
        wrapper.orderByDesc(Experiences::getStartDate);
        List<Experiences> experiences = baseMapper.selectList(wrapper);

        //2.转换为VO
        if (experiences != null&& !experiences.isEmpty()){
            return experiences.stream().map(experience ->
                    BeanUtil.copyProperties(experience, ExperienceVO.class))
                    .toList();
        }
        //3.如果为空则返回空列表
        return Collections.emptyList();
    }
}
