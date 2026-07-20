package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuan.dto.ArticleCommentPageQueryDTO;
import com.xuan.entity.ArticleComments;
import com.xuan.vo.ArticleCommentQueryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleCommentMapper extends BaseMapper<ArticleComments> {

    /**
     * 分页查询评论（关联文章标题）
     */
    IPage<ArticleCommentQueryVO> pageQueryWithArticleTitle(Page<ArticleComments> page,
                                                           @Param("dto") ArticleCommentPageQueryDTO dto);

    /**
     * 根据评论ID查询所属文章ID
     * <p>
     * 用于 {@link com.xuan.service.ArticlePermissionService#isCommentInOwnArticle} 校验：
     * 先通过评论ID查出 articleId，再判断该文章是否属于当前 AUTHOR 用户。
     * </p>
     *
     * @param commentId 评论ID
     * @return 文章ID；评论不存在时返回 null
     */
    @Select("SELECT article_id FROM article_comments WHERE id = #{commentId} LIMIT 1")
    Long selectArticleIdByCommentId(@Param("commentId") Long commentId);
}
