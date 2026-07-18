package com.xuan.controller.admin;

import com.xuan.annotation.OperationLog;
import com.xuan.dto.PersonalInfoDTO;
import com.xuan.entity.PersonalInfo;
import com.xuan.enumeration.OperationType;
import com.xuan.result.Result;
import com.xuan.service.IPersonalInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端个人信息接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（PUT）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@RestController("adminPersonalInfoController")
@RequestMapping("/admin/personalInfo")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class PersonalInfoController {

    private final IPersonalInfoService personalInfoService;

    /**
     * 获取个人信息
     */
    @GetMapping
    public Result<PersonalInfo> getPersonalInfo() {
        PersonalInfo personalInfo = personalInfoService.getAllPersonalInfo();
        return Result.success(personalInfo);
    }

    /**
     * 更新个人信息
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "personalInfo", targetId = "#personalInfoDTO.id")
    public Result updatePersonalInfo(@Valid @RequestBody PersonalInfoDTO personalInfoDTO) {
        log.info("更新个人信息: {}", personalInfoDTO);
        personalInfoService.updatePersonalInfo(personalInfoDTO);
        return Result.success();
    }
}
