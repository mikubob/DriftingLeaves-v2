package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.PersonalInfoDTO;
import com.xuan.entity.PersonalInfo;
import com.xuan.vo.PersonalInfoVO;

public interface IPersonalInfoService extends IService<PersonalInfo> {
    /**
     * 获取个人信息
     * @return
     */
    PersonalInfo getAllPersonalInfo();

    /**
     * 更新个人信息
     * @param personalInfoDTO
     */
    void updatePersonalInfo(PersonalInfoDTO personalInfoDTO);

    /**
     * 其他端获取个人信息
     * @return
     */
    PersonalInfoVO getPersonalInfo();
}
