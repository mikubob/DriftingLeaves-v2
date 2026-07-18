package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.dto.PersonalInfoDTO;
import com.xuan.entity.PersonalInfo;
import com.xuan.mapper.PersonalInfoMapper;
import com.xuan.service.IPersonalInfoService;
import com.xuan.vo.PersonalInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 个人信息服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalInfoServiceImpl extends ServiceImpl<PersonalInfoMapper, PersonalInfo> implements IPersonalInfoService {

    /**
     * 管理端获取所有个人信息
     * @return 个人信息
     */
    @Override
    @Cacheable(value = "personalInfo", key = "'all'", unless = "#result == null")
    public PersonalInfo getAllPersonalInfo() {
        //1.构建查询条件
        LambdaQueryWrapper<PersonalInfo> queryWrapper = new LambdaQueryWrapper<>();
        //2.添加查询条件
        queryWrapper.eq(PersonalInfo::getId, 1);
        //3.查询
        return baseMapper.selectOne(queryWrapper);

    }

    /**
     * 管理端更新个人信息
     * @param personalInfoDTO 个人信息
     */
    @Override
    @CacheEvict(value = "personalInfo", allEntries = true)
    public void updatePersonalInfo(PersonalInfoDTO personalInfoDTO) {
        LambdaUpdateWrapper<PersonalInfo> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PersonalInfo::getId, personalInfoDTO.getId());
        if (personalInfoDTO.getNickname() != null) {
            updateWrapper.set(PersonalInfo::getNickname, personalInfoDTO.getNickname());
        }
        if (personalInfoDTO.getTag() != null) {
            updateWrapper.set(PersonalInfo::getTag, personalInfoDTO.getTag());
        }
        if (personalInfoDTO.getDescription() != null) {
            updateWrapper.set(PersonalInfo::getDescription, personalInfoDTO.getDescription());
        }
        if (personalInfoDTO.getAvatar() != null) {
            updateWrapper.set(PersonalInfo::getAvatar, personalInfoDTO.getAvatar());
        }
        if (personalInfoDTO.getWebsite() != null) {
            updateWrapper.set(PersonalInfo::getWebsite, personalInfoDTO.getWebsite());
        }
        if (personalInfoDTO.getEmail() != null) {
            updateWrapper.set(PersonalInfo::getEmail, personalInfoDTO.getEmail());
        }
        if (personalInfoDTO.getGithub() != null) {
            updateWrapper.set(PersonalInfo::getGithub, personalInfoDTO.getGithub());
        }
        if (personalInfoDTO.getLocation() != null) {
            updateWrapper.set(PersonalInfo::getLocation, personalInfoDTO.getLocation());
        }
        update(updateWrapper);
    }

    /**
     * 获取个人信息
     * @return 个人信息
     */
    @Override
    @Cacheable(value = "personalInfo", key = "'vo'", unless = "#result == null")
    public PersonalInfoVO getPersonalInfo() {
        //1.构建查询条件
        LambdaQueryWrapper<PersonalInfo> queryWrapper = new LambdaQueryWrapper<>();
        //2.添加查询条件
        queryWrapper.eq(PersonalInfo::getId, 1);
        PersonalInfo info = baseMapper.selectOne(queryWrapper);
        //3.拷贝属性
        return BeanUtil.copyProperties(info, PersonalInfoVO.class);
    }
}
