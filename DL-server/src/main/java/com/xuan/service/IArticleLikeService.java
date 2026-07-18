package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.entity.ArticleLikes;

public interface IArticleLikeService extends IService<ArticleLikes> {

    /**
     * 点赞文章
     */
    void likeArticle(Long articleId, Long userId);

    /**
     * 取消点赞
     */
    void unlikeArticle(Long articleId, Long userId);

    /**
     * 检查是否已点赞
     */
    boolean hasLiked(Long articleId, Long userId);
}
