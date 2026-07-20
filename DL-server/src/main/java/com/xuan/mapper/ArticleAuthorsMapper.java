package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.ArticleAuthors;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章-作者关联 Mapper
 * <p>
 * 提供 article_authors 表的基础 CRUD（继承自 BaseMapper），
 * 以及 RBAC 权限校验所需的查询方法。
 * </p>
 * <p>
 * {@link com.xuan.service.ArticlePermissionService} 通过本 Mapper 判断 AUTHOR 角色用户
 * 是否为指定文章的作者（任意角色 / 第一作者），从而实现数据范围校验。
 * </p>
 */
@Mapper
public interface ArticleAuthorsMapper extends BaseMapper<ArticleAuthors> {

    /**
     * 判断用户是否为指定文章的作者（含共同作者）
     * <p>
     * 匹配条件：article_id + user_id + invite_status=1（已接受邀请）。
     * 第一作者、共同作者、通讯作者均会命中。
     * </p>
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return true=是作者；false=非作者或邀请未接受
     */
    @Select("SELECT COUNT(1) FROM article_authors " +
            "WHERE article_id = #{articleId} " +
            "  AND user_id = #{userId} " +
            "  AND invite_status = 1")
    boolean existsByArticleIdAndUserId(@Param("articleId") Long articleId,
                                       @Param("userId") Long userId);

    /**
     * 判断用户是否为指定文章的第一作者
     * <p>
     * 匹配条件：article_id + user_id + author_role=1（第一作者）+ invite_status=1（已接受）。
     * 第一作者拥有最高权限：删除文章、提交审核、邀请共同作者、撤回草稿。
     * </p>
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return true=是第一作者；false=非第一作者或邀请未接受
     */
    @Select("SELECT COUNT(1) FROM article_authors " +
            "WHERE article_id = #{articleId} " +
            "  AND user_id = #{userId} " +
            "  AND author_role = 1 " +
            "  AND invite_status = 1")
    boolean existsFirstAuthorByArticleIdAndUserId(@Param("articleId") Long articleId,
                                                  @Param("userId") Long userId);

    /**
     * 批量统计：用户作为第一作者的文章数量
     * <p>
     * 用于批量删除场景：传入多个 articleId，统计当前用户作为第一作者的文章数。
     * 调用方通过比较返回值与 articleIds.size() 判断是否全部为第一作者。
     * </p>
     * <p>
     * SQL 使用 IN 子句批量查询，避免 N+1 问题。
     * </p>
     *
     * @param articleIds 文章ID列表
     * @param userId     用户ID
     * @return 当前用户作为第一作者（已接受邀请）的文章数量；无匹配返回 0
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM article_authors " +
            "WHERE user_id = #{userId} " +
            "  AND author_role = 1 " +
            "  AND invite_status = 1 " +
            "  AND article_id IN " +
            "<foreach collection='articleIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    Long countFirstAuthorByArticleIdsAndUserId(@Param("articleIds") List<Long> articleIds,
                                               @Param("userId") Long userId);
}
