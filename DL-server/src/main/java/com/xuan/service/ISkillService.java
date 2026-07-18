package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.SkillDTO;
import com.xuan.entity.Skills;
import com.xuan.vo.SkillVO;

import java.util.List;

public interface ISkillService extends IService<Skills> {
    /**
     * 获取所有技能信息
     * @return
     */
    List<Skills> getAllSkill();

    /**
     * 添加技能
     * @param skillDTO
     */
    void addSkill(SkillDTO skillDTO);

    /**
     * 批量删除技能
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 修改技能
     * @param skillDTO
     */
    void updateSkill(SkillDTO skillDTO);

    /**
     * 简历端获取技能信息
     * @return
     */
    List<SkillVO> getSkillVO();
}
