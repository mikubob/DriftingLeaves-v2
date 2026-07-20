package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     * <p>
     * 用于博客端邮箱验证码登录：先按 email 定位 sys_user，再加载 UserDetails。
     * </p>
     */
    SysUser selectByEmail(@Param("email") String email);
}
