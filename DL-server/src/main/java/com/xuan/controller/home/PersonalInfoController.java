package com.xuan.controller.home;


import com.xuan.result.Result;
import com.xuan.service.IPersonalInfoService;
import com.xuan.vo.PersonalInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *  首页端个人信息接口
 */
@RestController("homePersonalInfoController")
@RequestMapping("/home/personalInfo")
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
