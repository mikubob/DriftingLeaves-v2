package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IMusicService;
import com.xuan.vo.MusicVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客端音乐接口
 */
@Slf4j
@RestController("blogMusicController")
@RequestMapping("/blog/music")
@RequiredArgsConstructor
public class MusicController {

    private final IMusicService musicService;

    /**
     * 获取所有可见的音乐
     * @return
     */
    @GetMapping
    public Result<List<MusicVO>> getAllVisibleMusic() {
        List<MusicVO> musicVOList = musicService.getAllVisibleMusic();
        return Result.success(musicVOList);
    }
}
