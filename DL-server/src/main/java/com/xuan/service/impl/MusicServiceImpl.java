package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.MusicDTO;
import com.xuan.dto.MusicPageQueryDTO;
import com.xuan.entity.Music;
import com.xuan.mapper.MusicMapper;
import com.xuan.result.PageResult;
import com.xuan.service.IMusicService;
import com.xuan.vo.MusicQueryVO;
import com.xuan.vo.MusicVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 音乐服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicServiceImpl extends ServiceImpl<MusicMapper, Music> implements IMusicService {

    /**
     * 添加音乐
     * @param musicDTO 音乐数据
     */
    @Override
    @CacheEvict(value = "musicList", allEntries = true)
    public void addMusic(MusicDTO musicDTO) {
        Music music = BeanUtil.copyProperties(musicDTO, Music.class);
        save(music);
    }

    /**
     * 分页查询音乐列表
     * @param musicPageQueryDTO 分页参数
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(MusicPageQueryDTO musicPageQueryDTO) {
        //1.创建分页对象
        Page<Music> page=new Page<>(musicPageQueryDTO.getPage(),musicPageQueryDTO.getPageSize());
        //2.创建查询条件
        Page<Music> musicPage = page(page, buildQueryWrapper(musicPageQueryDTO));
        //3.转换为 QueryVO 并返回
        Page<MusicQueryVO> voPage = new Page<>(musicPage.getCurrent(), musicPage.getSize(), musicPage.getTotal());
        voPage.setRecords(musicPage.getRecords().stream()
                .map(music -> BeanUtil.copyProperties(music, MusicQueryVO.class))
                .toList());
        return PageResult.fromIPage(voPage);
    }

    /**
     * 更新音乐
     * @param musicDTO
     */
    @Override
    @CacheEvict(value = "musicList", allEntries = true)
    public void updateMusic(MusicDTO musicDTO) {
        Music music = BeanUtil.copyProperties(musicDTO, Music.class);
        updateById(music);
    }

    /**
     * 批量删除音乐
     * @param ids 音乐id列表
     */
    @Override
    @CacheEvict(value = "musicList", allEntries = true)
    public void batchDelete(List<Long> ids) {
        removeByIds(ids);
    }

    /**
     * 根据 ID 查询音乐
     * @param id 音乐 ID
     * @return 音乐
     */
    @Override
    public Music getMusicWithId(Long id) {
        return getById(id);
    }

    /**
     * 获取所有可见的音乐
     * @return 所有可见的音乐
     */
    @Override
    @Cacheable(value = "musicList", key = "'visible'")
    public List<MusicVO> getAllVisibleMusic() {
        //1.构建查询条件
        LambdaQueryWrapper<Music> wrapper=new LambdaQueryWrapper<>();
        //2.插入查询条件
        wrapper.eq(Music::getIsVisible, StatusConstant.ENABLE);
        wrapper.orderByAsc(Music::getSort);
        wrapper.orderByDesc(Music::getId);
        //3.查询
        List<Music> musicList = list(wrapper);
        //4.转换为VO
        //4.1存在，返回有效数据
        if (musicList != null&& !musicList.isEmpty()){
            return musicList.stream().map(music ->
                            BeanUtil.copyProperties(music, MusicVO.class))
                    .toList();
        }
        //4.2不存在，返回空集合
        return Collections.emptyList();
    }

    //<==========私有辅助方法==============>
    private LambdaQueryWrapper<Music> buildQueryWrapper(MusicPageQueryDTO musicPageQueryDTO) {
        //1.构建查询条件
        LambdaQueryWrapper<Music> wrapper=new LambdaQueryWrapper<>();
        //2.添加查询条件
        if (StrUtil.isNotBlank(musicPageQueryDTO.getTitle())){
            wrapper.like(Music::getTitle,musicPageQueryDTO.getTitle());
        }
        if (StrUtil.isNotBlank(musicPageQueryDTO.getArtist())){
            wrapper.like(Music::getArtist,musicPageQueryDTO.getArtist());
        }
        if (musicPageQueryDTO.getIsVisible()!=null){
            wrapper.eq(Music::getIsVisible,musicPageQueryDTO.getIsVisible());
        }

        wrapper.orderByAsc(Music::getSort);
        wrapper.orderByDesc(Music::getId);
        //3.返回
        return wrapper;
    }
}
