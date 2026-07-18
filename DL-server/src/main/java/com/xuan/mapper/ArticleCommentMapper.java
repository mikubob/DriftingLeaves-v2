package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuan.dto.ArticleCommentPageQueryDTO;
import com.xuan.entity.ArticleComments;
import com.xuan.vo.ArticleCommentQueryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleCommentMapper extends BaseMapper<ArticleComments> {

    IPage<ArticleCommentQueryVO> pageQueryWithArticleTitle(Page<ArticleComments> page, @Param("dto") ArticleCommentPageQueryDTO dto);
}
