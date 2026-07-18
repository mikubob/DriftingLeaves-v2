package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.SocialMediaDTO;
import com.xuan.entity.SocialMedia;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.ISocialMediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *  管理端社交媒体接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@RestController("adminSocialMediaController")
@RequestMapping("/admin/socialMedia")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class SocialMediaController {

    private final ISocialMediaService socialMediaService;

    /**
     * 获取所有社交媒体信息
     */
    @GetMapping
    public Result<List<SocialMedia>> getAllSocialMedia() {
        List<SocialMedia> socialMediaList = socialMediaService.getAllSocialMedia();
        return Result.success(socialMediaList);
    }

    /**
     * 添加社交媒体信息
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "socialMedia")
    public Result addSocialMedia(@Valid @RequestBody SocialMediaDTO socialMediaDTO) {
        log.info("添加社交媒体信息: {}", socialMediaDTO);
        socialMediaService.addSocialMedia(socialMediaDTO);
        return Result.success();
    }
    /**
     * 批量删除社交媒体信息
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "socialMedia", targetId = "#ids")
    public Result deleteSocialMedia(@RequestParam List<Long> ids) {
        log.info("批量删除社交媒体信息: {}", ids);
        socialMediaService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 修改社交媒体信息
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "socialMedia", targetId = "#socialMediaDTO.id")
    public Result updateSocialMedia(@Valid @RequestBody SocialMediaDTO socialMediaDTO) {
        log.info("修改社交媒体信息: {}", socialMediaDTO);
        socialMediaService.updateSocialMedia(socialMediaDTO);
        return Result.success();
    }
}
