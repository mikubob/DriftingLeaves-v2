package com.xuan.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.ArticleCommentDTO;
import com.xuan.dto.ArticleCommentEditDTO;
import com.xuan.dto.ArticleCommentPageQueryDTO;
import com.xuan.dto.ArticleCommentReplyDTO;
import com.xuan.entity.ArticleComments;
import com.xuan.result.PageResult;
import com.xuan.vo.ArticleCommentVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 文章评论服务
 */
public interface IArticleCommentService extends IService<ArticleComments> {

    /**
     * 分页条件查询评论（时间、是否审核）
     * @param articleCommentPageQueryDTO
     * @return
     */
    PageResult pageQuery(ArticleCommentPageQueryDTO articleCommentPageQueryDTO);

    /**
     * 根据文章ID查询评论
     * @param articleId
     * @return
     */
    List<ArticleComments> getByArticleId(Long articleId);

    /**
     * 批量审核通过评论
     * @param ids
     */
    void batchApprove(List<Long> ids);

    /**
     * 批量删除评论
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 管理员回复评论
     * @param articleCommentReplyDTO
     * @param request
     */
    void adminReply(ArticleCommentReplyDTO articleCommentReplyDTO, HttpServletRequest request);

    // ===== 博客端方法 =====

    /**
     * 根据文章ID获取评论列表（树形结构，已审核 + 当前用户的未审核）
     */
    List<ArticleCommentVO> getCommentTree(Long articleId, Long userId);

    /**
     * 用户提交评论
     * <p>阶段四：userId 由 Controller 从 SecurityContext 取出后显式传入。</p>
     */
    void submitComment(ArticleCommentDTO articleCommentDTO, Long userId, HttpServletRequest request);

    /**
     * 用户编辑评论
     * <p>阶段四：userId 由 Controller 从 SecurityContext 取出后显式传入。</p>
     */
    void editComment(ArticleCommentEditDTO editDTO, Long userId);

    /**
     * 用户删除评论
     */
    void visitorDeleteComment(Long id, Long userId);

    /**
     * 统计总评论数
     * @return 总评论数
     */
    Integer countTotal();

    /**
     * 统计待审核评论数
     * @return 待审核评论数
     */
    Integer countPending();
}
