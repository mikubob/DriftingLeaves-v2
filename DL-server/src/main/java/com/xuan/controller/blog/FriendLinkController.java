package com.xuan.controller.blog;


import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import com.xuan.vo.FriendLinkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端友链接口
 */
@RestController("blogFriendLinkController")
@RequestMapping("/blog/friendLink")
@RequiredArgsConstructor
public class FriendLinkController {

    private final IFriendLinkService friendLinkService;

    /**
     * 获取可见友情链接
     */
    @GetMapping
    public Result<List<FriendLinkVO>> getVisibleFriendLink() {
        List<FriendLinkVO> friendLinkVOList = friendLinkService.getVisibleFriendLink();
        return Result.success(friendLinkVOList);
    }
}
