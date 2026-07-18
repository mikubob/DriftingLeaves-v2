package com.xuan.controller.blog;

import com.xuan.result.Result;
import com.xuan.service.IPersonalInfoService;
import com.xuan.vo.PersonalInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客端个人信息接口
 */
@RestController("blogPersonalInfoController")
@RequestMapping("/blog/personalInfo")
@RequiredArgsConstructor
public class PersonalInfoController {

    private final IPersonalInfoService personalInfoService;

    /**
     * 获取个人信息
     */
    @GetMapping
    public Result<PersonalInfoVO> getPersonalInfo() {
        PersonalInfoVO personalInfoVO = personalInfoService.getPersonalInfo();
        return Result.success(personalInfoVO);
    }
}
