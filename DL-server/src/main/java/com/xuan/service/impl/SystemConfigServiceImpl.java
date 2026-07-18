package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.MessageConstant;
import com.xuan.constant.RedisConstant;
import com.xuan.dto.SystemConfigDTO;
import com.xuan.entity.SystemConfig;
import com.xuan.exception.SystemConfigException;
import com.xuan.mapper.SystemConfigMapper;
import com.xuan.service.ISystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements ISystemConfigService {

    /**
     * 获取所有系统配置
     * @return 系统配置
     */
    @Override
    public List<SystemConfig> listAll() {
        LambdaQueryWrapper<SystemConfig> wrapper=new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SystemConfig::getId);
        return list(wrapper);
    }

    /**
     * 根据配置键获取配置
     * @param configKey 配置键
     * @return 系统配置
     */
    @Override
    @Cacheable(value = "systemConfig", key = "#configKey")
    public SystemConfig getByKey(String configKey) {
        LambdaQueryWrapper<SystemConfig> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, configKey);
        return getOne(wrapper);
    }

    /**
     * 根据 ID 获取配置
     * @param id 配置 ID
     * @return 系统配置
     */
    @Override
    public SystemConfig getConfigById(Long id) {
        return getById(id);
    }

    /**
     * 添加系统配置
     * @param systemConfigDTO 系统配置
     */
    @Override
    @CacheEvict(value = "systemConfig", allEntries = true)
    public void addConfig(SystemConfigDTO systemConfigDTO) {
        //1.检查配置键是否已存在
        LambdaQueryWrapper<SystemConfig> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, systemConfigDTO.getConfigKey());
        SystemConfig config = getOne(wrapper);
        if (config != null){
            throw new SystemConfigException(MessageConstant.CONFIG_KEY_EXISTS);
        }
        //2.保存系统配置
        SystemConfig systemConfig = BeanUtil.copyProperties(systemConfigDTO, SystemConfig.class);
        save(systemConfig);
    }

    /**
     * 修改系统配置
     * @param systemConfigDTO 系统配置
     */
    @Override
    @CacheEvict(value = "systemConfig", allEntries = true)
    public void updateConfig(SystemConfigDTO systemConfigDTO) {
        SystemConfig systemConfig = BeanUtil.copyProperties(systemConfigDTO, SystemConfig.class);
        updateById(systemConfig);
    }

    /**
     * 批量删除系统配置
     * @param ids 系统配置 ID 列表
     */
    @Override
    @CacheEvict(value = "systemConfig", allEntries = true)
    public void batchDelete(List<Long> ids) {
        removeByIds(ids);
    }
}
