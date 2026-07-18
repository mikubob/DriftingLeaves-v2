package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.FriendLinkDTO;
import com.xuan.entity.FriendLinks;
import com.xuan.mapper.FriendLinkMapper;
import com.xuan.service.IFriendLinkService;
import com.xuan.vo.FriendLinkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 友情链接服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkMapper, FriendLinks> implements IFriendLinkService {

    /**
     * 获取所有友情链接
     *
     * @return 友情链接列表
     */
    @Override
    @Cacheable(value = "friendLinks", key = "'all'")
    public List<FriendLinks> getAllFriendLink() {
        //1.构建查询条件
        LambdaQueryWrapper<FriendLinks> Wrapper = new LambdaQueryWrapper<>();
        Wrapper.orderByAsc(true, FriendLinks::getSort, FriendLinks::getId);
        //2.执行查询,并返回结果
        return list(Wrapper);
    }

    /**
     * 添加友情链接
     *
     * @param friendLinkDTO 友情链接数据
     */
    @Override
    @CacheEvict(value = "friendLinks", allEntries = true)
    public void addFriendLink(FriendLinkDTO friendLinkDTO) {
        //1.创建友情链接对象
        FriendLinks friendLinks = BeanUtil.copyProperties(friendLinkDTO, FriendLinks.class);
        //2.保存友情链接
        save(friendLinks);
    }

    /**
     * 批量删除友情链接
     *
     * @param ids 友情链接ID列表
     */
    @Override
    @CacheEvict(value = "friendLinks", allEntries = true)
    public void batchDelete(List<Long> ids) {
        removeByIds(ids);
    }

    /**
     * 修改友情链接
     *
     * @param friendLinkDTO 友情链接数据
     */
    @Override
    @CacheEvict(value = "friendLinks", allEntries = true)
    public void updateFriendLink(FriendLinkDTO friendLinkDTO) {
        FriendLinks friendLinks = BeanUtil.copyProperties(friendLinkDTO, FriendLinks.class);
        updateById(friendLinks);
    }

    /**
     * 获取可见友情链接
     *
     * @return 友情链接列表
     */
    @Override
    @Cacheable(value = "friendLinks", key = "'visible'")
    public List<FriendLinkVO> getVisibleFriendLink() {
        //1.构建查询条件
        LambdaQueryWrapper<FriendLinks> Wrapper = new LambdaQueryWrapper<>();
        Wrapper.eq(FriendLinks::getIsVisible, StatusConstant.ENABLE)
                .orderByAsc(true, FriendLinks::getSort, FriendLinks::getId);

        //2.执行查询
        List<FriendLinks> friendLinkList = list(Wrapper);

        //3.符合条件转换为VO并且返回
        if (friendLinkList != null && !friendLinkList.isEmpty()){
            return friendLinkList.stream().map(friendLink ->
                            BeanUtil.copyProperties(friendLink, FriendLinkVO.class))
                    .toList();
        }

        //4.不符合条件返回空集合
        return Collections.emptyList();
    }
}
