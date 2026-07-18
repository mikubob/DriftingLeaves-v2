package com.xuan.controller.cv;

import com.xuan.result.Result;
import com.xuan.service.ISkillService;
import com.xuan.vo.SkillVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 简历端技能接口
 */
@RestController("cvSkillController")
@RequestMapping("/cv/skill")
@RequiredArgsConstructor
public class SkillController {

    private final ISkillService skillService;

    /**
     * 获取技能信息
     */
    @GetMapping
    public Result<List<SkillVO>> getSkill() {
        List<SkillVO> skillList = skillService.getSkillVO();
        return Result.success(skillList);
    }
}
