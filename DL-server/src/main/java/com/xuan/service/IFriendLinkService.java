package com.xuan.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.FriendLinkDTO;
import com.xuan.entity.FriendLinks;
import com.xuan.vo.FriendLinkVO;

import java.util.List;

public interface IFriendLinkService extends IService<FriendLinks> {
    /**
     * 管理端获取所有友情链接
     * @return
     */
    List<FriendLinks> getAllFriendLink();

    /**
     * 管理端添加友情链接
     * @param friendLinkDTO
     */
    void addFriendLink(FriendLinkDTO friendLinkDTO);

    /**
     * 批量删除友情链接
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 管理端修改友情链接
     * @param friendLinkDTO
     */
    void updateFriendLink(FriendLinkDTO friendLinkDTO);

    /**
     * 博客端获取可见友情链接
     * @return
     */
    List<FriendLinkVO> getVisibleFriendLink();
}
