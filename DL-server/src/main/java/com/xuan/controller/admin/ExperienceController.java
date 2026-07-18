package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.ExperienceDTO;
import com.xuan.entity.Experiences;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.IExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *  管理端经历接口
 */
@RestController("adminExperienceController")
@RequestMapping("/admin/experience")
@Slf4j
@RequiredArgsConstructor
public class ExperienceController {

    private final IExperienceService experienceService;

    /**
     * 根据分类获取经历信息
     */
    @GetMapping
    public Result<List<Experiences>> getExperience(@RequestParam(required = false) Integer type) {
        log.info("根据分类获取经历信息,{}", type);
        List<Experiences> experienceList = experienceService.getExperience(type);
        return Result.success(experienceList);
    }

    /**
     * 添加经历信息
     */
    @PostMapping
    @OperationLog(value = OperationType.INSERT, target = "experience")
    public Result addExperience(@Valid @RequestBody ExperienceDTO experienceDTO) {
        log.info("添加经历信息,{}", experienceDTO);
        experienceService.addExperience(experienceDTO);
        return Result.success();
    }

    /**
     * 修改经历信息
     */
    @PutMapping
    @OperationLog(value = OperationType.UPDATE, target = "experience", targetId = "#experienceDTO.id")
    public Result updateExperience(@Valid @RequestBody ExperienceDTO experienceDTO) {
        log.info("修改经历信息,{}", experienceDTO);
        experienceService.updateExperience(experienceDTO);
        return Result.success();
    }

    /**
     * 批量删除经历信息
     */
    @DeleteMapping
    @OperationLog(value = OperationType.DELETE, target = "experience", targetId = "#ids")
    public Result deleteExperience(@RequestParam List<Long> ids) {
        log.info("批量删除经历信息,{}", ids);
        experienceService.batchDelete(ids);
        return Result.success();
    }

}
