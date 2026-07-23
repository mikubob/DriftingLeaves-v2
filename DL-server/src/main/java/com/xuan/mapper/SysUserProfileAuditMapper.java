package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUserProfileAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserProfileAuditMapper extends BaseMapper<SysUserProfileAudit> {

    /**
     * 查询用户指定类型的待审核记录
     */
    SysUserProfileAudit selectPendingByUserAndType(@Param("userId") Long userId,
                                                   @Param("auditType") Integer auditType);

    /**
     * 查询用户指定类型最近一次审核通过记录
     */
    SysUserProfileAudit selectLatestApprovedByUser(@Param("userId") Long userId,
                                                   @Param("auditType") Integer auditType);

    /**
     * 查询指定IP指定类型最近一次审核通过记录
     */
    SysUserProfileAudit selectLatestApprovedByIp(@Param("applyIp") String applyIp,
                                                 @Param("auditType") Integer auditType);

    /**
     * 查询所有待审核记录（按申请时间倒序）
     */
    java.util.List<SysUserProfileAudit> selectPendingList();
}
