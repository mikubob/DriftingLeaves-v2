package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.MusicDTO;
import com.xuan.dto.MusicPageQueryDTO;
import com.xuan.entity.Music;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMusicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端音乐接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@Slf4j
@RestController("adminMusicController")
@RequestMapping("/admin/music")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class MusicController {

    private final IMusicService musicService;

    /**
     * 分页查询音乐列表
     * @param musicPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> getMusicList(MusicPageQueryDTO musicPageQueryDTO) {
        log.info("获取音乐列表,{}", musicPageQueryDTO);
        PageResult pageResult = musicService.pageQuery(musicPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据ID查询音乐
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<Music> getById(@PathVariable Long id) {
        log.info("根据ID查询音乐,{}", id);
        Music music = musicService.getMusicWithId(id);
        return Result.success(music);
    }

    /**
     * 添加音乐
     * @param musicDTO
     * @return
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "music")
    public Result addMusic(@Valid @RequestBody MusicDTO musicDTO) {
        log.info("添加音乐,{}", musicDTO);
        musicService.addMusic(musicDTO);
        return Result.success();
    }

    /**
     * 更新音乐
     * @param musicDTO
     * @return
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "music", targetId = "#musicDTO.id")
    public Result updateMusic(@Valid @RequestBody MusicDTO musicDTO) {
        log.info("更新音乐,{}", musicDTO);
        musicService.updateMusic(musicDTO);
        return Result.success();
    }

    /**
     * 批量删除音乐
     * @param ids
     * @return
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "music", targetId = "#ids")
    public Result deleteMusic(@RequestParam List<Long> ids) {
        log.info("批量删除音乐,{}", ids);
        musicService.batchDelete(ids);
        return Result.success();
    }
}
