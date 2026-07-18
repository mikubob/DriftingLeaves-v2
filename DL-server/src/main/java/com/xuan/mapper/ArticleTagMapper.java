package com.xuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuan.entity.ArticleTagRelations;
import com.xuan.entity.ArticleTags;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTags> {

    List<ArticleTags> getVisibleTags();

    List<ArticleTags> getTagByArticleId(Long articleId);

    List<Long> getTagIdsByArticleId(Long articleId);

    int deleteRelationsByArticleId(Long articleId);

    int batchInsertRelations(@Param("relations") List<ArticleTagRelations> relations);

    int batchDeleteRelationsByArticleIds(@Param("articleIds") List<Long> articleIds);

    int deleteRelationsByTagIds(@Param("tagIds") List<Long> tagIds);
}
