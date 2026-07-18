package com.xuan.controller.blog;

import com.xuan.entity.SystemConfig;
import com.xuan.result.Result;
import com.xuan.service.ISystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController("blogSystemConfigController")
@RequestMapping("/blog/systemConfig")
@RequiredArgsConstructor
public class SystemConfigController {

    private final ISystemConfigService systemConfigService;

    /**
     * 根据配置键获取配置
     * @param configKey
     * @return
     */
    @GetMapping("/key/{configKey}")
    public Result<SystemConfig> getByKey(@PathVariable String configKey) {
        log.info("根据配置键获取配置,{}", configKey);
        SystemConfig systemConfig = systemConfigService.getByKey(configKey);
        return Result.success(systemConfig);
    }
}
