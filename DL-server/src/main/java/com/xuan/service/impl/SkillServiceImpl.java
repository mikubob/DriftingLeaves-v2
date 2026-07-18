package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.SkillDTO;
import com.xuan.entity.Skills;
import com.xuan.mapper.SkillMapper;
import com.xuan.service.ISkillService;
import com.xuan.vo.SkillVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 技能服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skills> implements ISkillService {

    /**
     * 获取所有技能信息
     *
     * @return 技能信息
     */
    @Override
    @Cacheable(value = "skills", key = "'all'")
    public List<Skills> getAllSkill() {
        // 按 sort 排序
        LambdaQueryWrapper<Skills> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Skills::getSort);
        return list(queryWrapper);
    }

    /**
     * 添加技能信息
     *
     * @param skillDTO 技能信息
     */
    @Override
    @CacheEvict(value = "skills", allEntries = true)
    public void addSkill(SkillDTO skillDTO) {
        Skills skills = BeanUtil.copyProperties(skillDTO, Skills.class);
        save(skills);
    }

    /**
     * 批量删除技能信息
     *
     * @param ids 技能ID列表
     */
    @Override
    @CacheEvict(value = "skills", allEntries = true)
    public void batchDelete(List<Long> ids) {
        removeByIds(ids);
    }

    /**
     * 修改技能信息
     *
     * @param skillDTO 技能信息
     */
    @Override
    @CacheEvict(value = "skills", allEntries = true)
    public void updateSkill(SkillDTO skillDTO) {
        Skills skills = BeanUtil.copyProperties(skillDTO, Skills.class);
        updateById(skills);
    }

    /**
     * 获取技能信息
     *
     * @return 技能信息
     */
    @Override
    @Cacheable(value = "skills", key = "'visible'")
    public List<SkillVO> getSkillVO() {
        //1.构建查询条件
        LambdaQueryWrapper<Skills> queryWrapper = new LambdaQueryWrapper<>();
        //2.添加查询条件
        queryWrapper.eq(Skills::getIsVisible, StatusConstant.ENABLE);
        queryWrapper.orderByAsc(Skills::getSort);
        //3.查询
        List<Skills> skillsList = list(queryWrapper);
        //4.转换为vo并且返回
        //4.1. 如果不是空则返回具体数据集合
        if (skillsList != null && !skillsList.isEmpty()){
            return skillsList.stream()
                    .map(skills -> BeanUtil.copyProperties(skills, SkillVO.class))
                    .toList();
        }
        //4.2. 如果是空则返回空集合
        return Collections.emptyList();
    }
}
