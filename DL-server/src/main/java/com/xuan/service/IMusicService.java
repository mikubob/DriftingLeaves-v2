package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.MusicDTO;
import com.xuan.dto.MusicPageQueryDTO;
import com.xuan.entity.Music;
import com.xuan.result.PageResult;
import com.xuan.vo.MusicVO;

import java.util.List;

public interface IMusicService extends IService<Music> {
    /**
     * 添加音乐
     * @param musicDTO
     */
    void addMusic(MusicDTO musicDTO);

    /**
     * 分页查询音乐列表
     * @param musicPageQueryDTO
     * @return
     */
    PageResult pageQuery(MusicPageQueryDTO musicPageQueryDTO);

    /**
     * 更新音乐
     * @param musicDTO
     */
    void updateMusic(MusicDTO musicDTO);

    /**
     * 批量删除音乐
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 根据ID查询音乐
     * @param id
     * @return
     */
    Music getMusicWithId(Long id);

    /**
     * 获取所有可见的音乐
     * @return
     */
    List<MusicVO> getAllVisibleMusic();
}
