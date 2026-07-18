package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.OAuth2Authorization;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OAuth2AuthorizationMapper extends BaseMapper<OAuth2Authorization> {
}
