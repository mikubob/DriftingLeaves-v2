package com.xuan.controller.admin;

import com.xuan.annotation.OperationLog;
import com.xuan.dto.SkillDTO;
import com.xuan.entity.Skills;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.ISkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端技能接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@RestController("adminSkillController")
@RequestMapping("/admin/skill")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class SkillController {

    private final ISkillService skillService;

    /**
     * 获取所有技能信息
     */
    @GetMapping
    public Result<List<Skills>> getAllSkill() {
        return Result.success(skillService.getAllSkill());
    }

    /**
     * 添加技能信息
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "skill")
    public Result addSkill(@Valid @RequestBody SkillDTO skillDTO) {
        log.info("添加技能信息,{}", skillDTO);
        skillService.addSkill(skillDTO);
        return Result.success();
    }

    /**
     * 批量删除技能信息
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "skill", targetId = "#ids")
    public Result<String> deleteSkill(@RequestParam List<Long> ids) {
        log.info("批量删除技能信息,{}", ids);
        skillService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 修改技能信息
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "skill", targetId = "#skillDTO.id")
    public Result updateSkill(@Valid @RequestBody SkillDTO skillDTO) {
        log.info("修改技能信息,{}", skillDTO);
        skillService.updateSkill(skillDTO);
        return Result.success();
    }

}
