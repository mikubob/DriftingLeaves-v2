package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.FriendLinkDTO;
import com.xuan.entity.FriendLinks;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.IFriendLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端友链接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@RestController("adminFriendLinkController")
@RequestMapping("/admin/friendLink")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class FriendLinkController {

    private final IFriendLinkService friendLinkService;

    /**
     * 获取所有友情链接信息
     */
    @GetMapping
    public Result<List<FriendLinks>> getAllFriendLink() {
        List<FriendLinks> friendLinkList = friendLinkService.getAllFriendLink();
        return Result.success(friendLinkList);
    }

    /**
     * 添加友情链接信息
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "friendLink")
    public Result addFriendLink(@Valid @RequestBody FriendLinkDTO friendLinkDTO) {
        log.info("添加友情链接信息:{}", friendLinkDTO);
        friendLinkService.addFriendLink(friendLinkDTO);
        return Result.success();
    }

    /**
     * 批量删除友情链接信息
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "friendLink", targetId = "#ids")
    public Result deleteFriendLink(@RequestParam List<Long> ids) {
        log.info("批量删除友情链接信息:{}", ids);
        friendLinkService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 修改友情链接信息
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "friendLink", targetId = "#friendLinkDTO.id")
    public Result updateFriendLink(@Valid @RequestBody FriendLinkDTO friendLinkDTO) {
        log.info("修改友情链接信息:{}", friendLinkDTO);
        friendLinkService.updateFriendLink(friendLinkDTO);
        return Result.success();
    }
}
