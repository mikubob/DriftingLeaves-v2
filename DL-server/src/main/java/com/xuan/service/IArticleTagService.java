package com.xuan.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.ArticleTagDTO;
import com.xuan.entity.ArticleTags;

import java.util.List;

public interface IArticleTagService extends IService<ArticleTags> {

    /**
     * 获取所有标签
     */
    List<ArticleTags> listAll();

    /**
     * 添加标签
     * @param articleTagDTO
     * @return
     */
    void addTag(ArticleTagDTO articleTagDTO);

    /**
     * 修改标签
     * @param articleTagDTO
     * @return
     */
    void updateTag(ArticleTagDTO articleTagDTO);

    /**
     * 批量删除标签
     * @param ids
     * @return
     */
    void batchDeleteTags(List<Long> ids);

    /**
     * 获取有已发布文章的标签列表（博客端）
     * @return
     */
    List<ArticleTags> getVisibleTags();

    //<====其他实现类调用的业务接口=====>
    /**
     * 根据文章id获取标签id
     *
     * @param id
     * @return
     */
    List<Long> getTagIdsByArticleId(Long id);

    /**
     * 根据文章id删除标签关联
     * @param id
     */
    void deleteRelationsByArticleId(Long id);

    /**
     * 批量插入标签关联
     * @param id
     * @param tagIds
     */
    void batchInsertRelations(Long id, List<Long> tagIds);

    /**
     * 批量删除标签关联
     * @param ids
     */
    void batchDeleteRelationsByArticleIds(List<Long> ids);

    /**
     * 根据文章id获取标签
     * @param id
     * @return
     */
    List<ArticleTags> getTagByArticleId(Long id);

    /**
     * 获取标签总数
     * @return 标签总数
     */
    Integer countTotal();
}
