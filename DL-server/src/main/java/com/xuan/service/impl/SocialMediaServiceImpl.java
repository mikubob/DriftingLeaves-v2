package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.SocialMediaDTO;
import com.xuan.entity.SocialMedia;
import com.xuan.mapper.SocialMediaMapper;
import com.xuan.service.ISocialMediaService;
import com.xuan.vo.SocialMediaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 社交媒体服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialMediaServiceImpl extends ServiceImpl<SocialMediaMapper, SocialMedia> implements ISocialMediaService {

    /**
     * 获取可见社交媒体信息
     *
     * @return 社交媒体信息
     */
    @Override
    @Cacheable(value = "socialMedia", key = "'visible'")
    public List<SocialMediaVO> getVisibleSocialMedia() {
        //1. 构建查询条件
        LambdaQueryWrapper<SocialMedia> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SocialMedia::getIsVisible, StatusConstant.ENABLE);
        //2. 查询
        List<SocialMedia> socialMediaList = list(wrapper);
        //3.转换为vo并且判断是否需要换回
        if (socialMediaList != null && !socialMediaList.isEmpty()) {
            //3.1 不为空转换为vo集合并且返回
            return socialMediaList.stream()
                    .map(socialMedia -> BeanUtil.copyProperties(socialMedia, SocialMediaVO.class))
                    .toList();
        }
        //3.2 为空则返回空集合
        return Collections.emptyList();
    }

    /**
     * 获取所有社交媒体信息
     *
     * @return 社交媒体信息
     */
    @Override
    @Cacheable(value = "socialMedia", key = "'all'")
    public List<SocialMedia> getAllSocialMedia() {
        List<SocialMedia> socialMediaList = list();
        if (socialMediaList != null && !socialMediaList.isEmpty()) {
            return socialMediaList;
        }
        return Collections.emptyList();
    }

    /**
     * 添加社交媒体信息
     *
     * @param socialMediaDTO 社交媒体信息
     */
    @Override
    @CacheEvict(value = "socialMedia", allEntries = true)
    public void addSocialMedia(SocialMediaDTO socialMediaDTO) {
        SocialMedia socialMedia = BeanUtil.copyProperties(socialMediaDTO, SocialMedia.class);
        save(socialMedia);
    }

    /**
     * 批量删除社交媒体信息
     * @param ids 社交媒体id列表
     */
    @Override
    @CacheEvict(value = "socialMedia", allEntries = true)
    public void batchDelete(List<Long> ids) {
        removeByIds(ids);
    }

    /**
     * 修改社交媒体信息
     * @param socialMediaDTO 社交媒体信息
     */
    @Override
    @CacheEvict(value = "socialMedia", allEntries = true)
    public void updateSocialMedia(SocialMediaDTO socialMediaDTO) {
        SocialMedia socialMedia = BeanUtil.copyProperties(socialMediaDTO, SocialMedia.class);
        updateById(socialMedia);
    }
}
