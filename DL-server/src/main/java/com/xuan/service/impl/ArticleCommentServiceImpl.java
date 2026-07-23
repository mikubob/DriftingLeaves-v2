package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.MessageConstant;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.ArticleCommentDTO;
import com.xuan.dto.ArticleCommentEditDTO;
import com.xuan.dto.ArticleCommentPageQueryDTO;
import com.xuan.dto.ArticleCommentReplyDTO;
import com.xuan.entity.ArticleComments;
import com.xuan.entity.Articles;
import com.xuan.entity.SysUser;
import com.xuan.exception.ValidationException;
import com.xuan.mapper.ArticleCommentMapper;
import com.xuan.mapper.SysUserMapper;
import com.xuan.properties.WebsiteProperties;
import com.xuan.result.PageResult;
import com.xuan.service.AsyncEmailService;
import com.xuan.service.IArticleCommentService;
import com.xuan.service.IArticleService;
import com.xuan.service.UserAgentService;
import com.xuan.utils.IpUtil;
import com.xuan.utils.MarkdownUtil;
import com.xuan.vo.ArticleCommentQueryVO;
import com.xuan.vo.ArticleCommentVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章评论服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCommentServiceImpl extends ServiceImpl<ArticleCommentMapper, ArticleComments> implements IArticleCommentService {

    private final IArticleService articleService;
    private final UserAgentService userAgentService;
    private final AsyncEmailService asyncEmailService;
    private final WebsiteProperties websiteProperties;
    private final ArticleCommentMapper articleCommentMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 分页查询评论
     *
     * @param articleCommentPageQueryDTO 评论分页查询参数
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(ArticleCommentPageQueryDTO articleCommentPageQueryDTO) {
        Page<ArticleComments> page = new Page<>(articleCommentPageQueryDTO.getPage(), articleCommentPageQueryDTO.getPageSize());
        IPage<ArticleCommentQueryVO> resultPage = articleCommentMapper.pageQueryWithArticleTitle(page, articleCommentPageQueryDTO);
        return PageResult.fromIPage(resultPage);
    }

    /**
     * 根据文章 ID 查询评论
     *
     * @param articleId 文章 ID
     * @return 评论列表
     */
    @Override
    public List<ArticleComments> getByArticleId(Long articleId) {
        // 构建查询条件
        LambdaQueryWrapper<ArticleComments> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleComments::getArticleId, articleId)
                .orderByDesc(ArticleComments::getCreateTime);

        // 执行查询
        return this.list(wrapper);
    }

    /**
     * 批量审核通过评论
     *
     * @param ids 评论 ID 列表
     */
    @Override
    @Transactional
    public void batchApprove(List<Long> ids) {
        //1.遍历每条评论，只对"当前未审核"的评论增加文章评论数
        for (Long id : ids) {
            ArticleComments comment = getById(id);
            if (comment != null && comment.getArticleId() != null
                    && (comment.getIsApproved() == null || comment.getIsApproved() == 0)) {
                incrementArticleCommentCount(comment.getArticleId());
            }
        }

        //2.批量更新审核状态为通过
        List<ArticleComments> updateList = new ArrayList<>();
        for (Long id : ids) {
            ArticleComments comment = new ArticleComments();
            comment.setId(id);
            comment.setIsApproved(StatusConstant.ENABLE);
            updateList.add(comment);
        }
        this.updateBatchById(updateList);
    }

    /**
     * 批量删除评论
     *
     * @param ids 评论 ID 列表
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        //1.遍历每条评论
        for (Long id : ids) {
            ArticleComments comment = getById(id);
            if (comment == null || comment.getArticleId() == null) {
                continue;
            }

            //2.如果是根评论，级联删除所有子评论
            if (comment.getRootId() == null || comment.getRootId() == 0) {
                // 查询已审核子评论数量
                LambdaQueryWrapper<ArticleComments> approvedChildWrapper = new LambdaQueryWrapper<>();
                approvedChildWrapper.eq(ArticleComments::getRootId, id)
                        .eq(ArticleComments::getIsApproved, StatusConstant.ENABLE);
                Long approvedChildCount = this.count(approvedChildWrapper);
                if (approvedChildCount != null && approvedChildCount > 0) {
                    for (int i = 0; i < approvedChildCount; i++) {
                        decrementArticleCommentCount(comment.getArticleId());
                    }
                }

                // 查询是否存在子评论
                LambdaQueryWrapper<ArticleComments> childWrapper = new LambdaQueryWrapper<>();
                childWrapper.eq(ArticleComments::getRootId, id);
                Long totalChildCount = this.count(childWrapper);
                if (totalChildCount != null && totalChildCount > 0) {
                    this.remove(childWrapper);
                }
            }

            //4.只有已审核的评论才减少文章评论数
            if (comment.getIsApproved() != null && comment.getIsApproved() == 1) {
                decrementArticleCommentCount(comment.getArticleId());
            }
        }

        //5.批量删除评论
        removeBatchByIds(ids);
    }

    /**
     * 管理员回复评论
     *
     * @param articleCommentReplyDTO 管理员回复 DTO
     * @param request HTTP 请求
     */
    @Override
    public void adminReply(ArticleCommentReplyDTO articleCommentReplyDTO, HttpServletRequest request) {
        //1.复制属性
        ArticleComments articleComments = BeanUtil.copyProperties(articleCommentReplyDTO, ArticleComments.class);

        //2.处理 Markdown 内容
        if (articleCommentReplyDTO.getIsMarkdown() != null && articleCommentReplyDTO.getIsMarkdown() == 1) {
            String html = MarkdownUtil.toHtml(articleCommentReplyDTO.getContent());
            articleComments.setContentHtml(html);
        } else {
            articleComments.setContentHtml(articleCommentReplyDTO.getContent());
        }

        //3.设置管理员回复标识
        articleComments.setIsAdminReply(StatusConstant.ENABLE);
        articleComments.setIsApproved(StatusConstant.ENABLE);
        articleComments.setIsEdited(StatusConstant.DISABLE);

        //4.捕获 IP / 地理位置 / UserAgent
        if (request != null) {
            String clientIp = IpUtil.getClientIp(request);
            Map<String, String> geoInfo = IpUtil.getGeoInfo(clientIp);
            String province = geoInfo.getOrDefault("province", "");
            String city = geoInfo.getOrDefault("city", "");
            String location;
            if (province.isEmpty() && city.isEmpty()) {
                location = null;
            } else if (province.equals(city)) {
                location = province;
            } else {
                location = String.format("%s-%s", province, city).replaceAll("^-|-$", "");
            }
            if (location != null) {
                articleComments.setLocation(location);
            }

            String userAgent = request.getHeader("User-Agent");
            articleComments.setUserAgentOs(userAgentService.getOsName(userAgent));
            articleComments.setUserAgentBrowser(userAgentService.getBrowserName(userAgent));
        }

        //5.保存评论
        this.save(articleComments);

        //6.管理员回复自动通过审核，文章评论数 +1
        if (articleCommentReplyDTO.getArticleId() != null) {
            incrementArticleCommentCount(articleCommentReplyDTO.getArticleId());
        }

        //7.检查父评论是否开启邮箱通知
        notifyParentIfNeeded(articleCommentReplyDTO.getParentId(), websiteProperties.getTitle(),
                articleCommentReplyDTO.getContent(), "comment");

        log.info("管理员回复评论成功：parentId={}", articleCommentReplyDTO.getParentId());
    }

    /**
     * 根据文章 ID 获取评论列表（树形结构）
     *
     * @param articleId 文章 ID
     * @param userId 用户 ID
     * @return 评论树形列表
     */
    @Override
    public List<ArticleCommentVO> getCommentTree(Long articleId, Long userId) {
        //1.查询评论列表（已审核 + 当前用户的未审核评论）
        LambdaQueryWrapper<ArticleComments> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleComments::getArticleId, articleId)
                .and(w -> {
                    w.eq(ArticleComments::getIsApproved, StatusConstant.ENABLE);
                    if (userId != null) {
                        w.or().eq(ArticleComments::getUserId, userId);
                    }
                })
                .orderByAsc(ArticleComments::getCreateTime);

        List<ArticleComments> allComments = this.list(wrapper);

        //2.转换为 VO，并批量填充用户昵称/头像
        List<ArticleCommentVO> allVOs = allComments.stream()
                .map(this::convertToVO)
                .toList();
        fillUserInfo(allVOs);

        //3.构建树形结构：根评论作为一级，子评论挂到根评论下
        List<ArticleCommentVO> rootComments = new ArrayList<>();
        Map<Long, ArticleCommentVO> commentMap = allVOs.stream()
                .collect(Collectors.toMap(ArticleCommentVO::getId, c -> c));

        for (ArticleCommentVO comment : allVOs) {
            if (comment.getRootId() == null || comment.getRootId() == 0) {
                // 根评论
                comment.setChildren(new ArrayList<>());
                rootComments.add(comment);
            } else {
                // 子评论，挂到根评论下
                ArticleCommentVO rootComment = commentMap.get(comment.getRootId());
                if (rootComment != null) {
                    if (rootComment.getChildren() == null) {
                        rootComment.setChildren(new ArrayList<>());
                    }
                    rootComment.getChildren().add(comment);
                }
            }
        }

        return rootComments;
    }

    /**
     * 提交评论（添加评论/回复评论）
     *
     * @param articleCommentDTO 评论 DTO
     * @param userId 当前登录用户 ID（由 Controller 从 SecurityContext 取出）
     * @param request HTTP 请求
     */
    @Override
    @Transactional
    public void submitComment(ArticleCommentDTO articleCommentDTO, Long userId, HttpServletRequest request) {
        //1.复制属性
        ArticleComments articleComments = BeanUtil.copyProperties(articleCommentDTO, ArticleComments.class);
        //1.1 显式设置 userId（DTO 已移除该字段）
        articleComments.setUserId(userId);

        //2.处理 Markdown 内容
        if (articleCommentDTO.getIsMarkdown() != null && articleCommentDTO.getIsMarkdown() == 1) {
            String html = MarkdownUtil.toHtml(articleCommentDTO.getContent());
            articleComments.setContentHtml(html);
        } else {
            articleComments.setContentHtml(articleCommentDTO.getContent());
        }

        //3.获取 IP 地址信息
        String clientIp = IpUtil.getClientIp(request);
        Map<String, String> geoInfo = IpUtil.getGeoInfo(clientIp);
        String province = geoInfo.getOrDefault("province", "");
        String city = geoInfo.getOrDefault("city", "");
        String location;
        if (province.isEmpty() && city.isEmpty()) {
            location = null;
        } else if (province.equals(city)) {
            location = province;
        } else {
            location = String.format("%s-%s", province, city).replaceAll("^-|-$", "");
        }
        if (location != null) {
            articleComments.setLocation(location);
        }

        //4.解析 UserAgent
        String userAgent = request.getHeader("User-Agent");
        String osName = userAgentService.getOsName(userAgent);
        String browserName = userAgentService.getBrowserName(userAgent);
        articleComments.setUserAgentOs(osName);
        articleComments.setUserAgentBrowser(browserName);

        //5.设置默认值
        articleComments.setIsApproved(StatusConstant.DISABLE);
        articleComments.setIsEdited(StatusConstant.DISABLE);
        articleComments.setIsAdminReply(StatusConstant.DISABLE);

        //6.保存评论
        this.save(articleComments);

        //7.检查父评论是否开启邮箱通知
        if (articleCommentDTO.getParentId() != null) {
            notifyParentIfNeeded(articleCommentDTO.getParentId(),
                    "", articleCommentDTO.getContent(), "comment");
        }

        log.info("用户提交文章评论成功：{}", articleComments);
    }

    /**
     * 访客编辑评论
     *
     * @param editDTO 编辑评论 DTO
     * @param userId 当前登录用户 ID（由 Controller 从 SecurityContext 取出）
     */
    @Override
    public void editComment(ArticleCommentEditDTO editDTO, Long userId) {
        //1.查询评论
        ArticleComments comment = getById(editDTO.getId());
        if (comment == null) {
            throw new ValidationException(MessageConstant.COMMENT_NOT_FOUND);
        }
        //2.验证身份
        if (!comment.getUserId().equals(userId)) {
            throw new ValidationException(MessageConstant.COMMENT_NOT_EDIT);
        }

        //3.构建更新对象
        ArticleComments updateComment = new ArticleComments();
        updateComment.setId(editDTO.getId());
        updateComment.setContent(editDTO.getContent());

        //4.处理 Markdown 内容
        if (editDTO.getIsMarkdown() != null && editDTO.getIsMarkdown() == 1) {
            updateComment.setContentHtml(MarkdownUtil.toHtml(editDTO.getContent()));
        } else {
            updateComment.setContentHtml(editDTO.getContent());
        }

        //5.更新评论
        this.updateById(updateComment);

        log.info("用户编辑评论成功：id={}, userId={}", editDTO.getId(), userId);
    }

    /**
     * 用户删除评论
     *
     * @param id 评论 ID
     * @param userId 用户 ID
     */
    @Override
    @Transactional
    public void visitorDeleteComment(Long id, Long userId) {
        //1.查询评论
        ArticleComments comment = getById(id);
        if (comment == null) {
            throw new ValidationException(MessageConstant.COMMENT_NOT_FOUND);
        }
        //2.验证身份
        if (!comment.getUserId().equals(userId)) {
            throw new ValidationException(MessageConstant.COMMENT_NOT_DELETE);
        }

        //3.如果是根评论，级联删除所有子评论
        if (comment.getRootId() == null || comment.getRootId() == 0) {
            // 查询已审核子评论数量
            LambdaQueryWrapper<ArticleComments> approvedChildWrapper = new LambdaQueryWrapper<>();
            approvedChildWrapper.eq(ArticleComments::getRootId, id)
                    .eq(ArticleComments::getIsApproved, StatusConstant.ENABLE);
            Long approvedChildCount = this.count(approvedChildWrapper);
            if (approvedChildCount != null && approvedChildCount > 0) {
                for (int i = 0; i < approvedChildCount; i++) {
                    decrementArticleCommentCount(comment.getArticleId());
                }
            }

            // 查询是否存在子评论
            LambdaQueryWrapper<ArticleComments> childWrapper = new LambdaQueryWrapper<>();
            childWrapper.eq(ArticleComments::getRootId, id);
            Long totalChildCount = this.count(childWrapper);
            if (totalChildCount != null && totalChildCount > 0) {
                this.remove(childWrapper);
            }
        }

        //5.删除评论
        this.removeById(id);

        //6.只有已审核的评论才减少文章评论数
        if (comment.getIsApproved() != null && comment.getIsApproved() == 1) {
            decrementArticleCommentCount(comment.getArticleId());
        }

        log.info("用户删除评论成功：id={}, userId={}", id, userId);
    }

    /**
     * 转换为 VO
     *
     * @param comment 评论实体
     * @return 评论 VO
     */
    private ArticleCommentVO convertToVO(ArticleComments comment) {
        return BeanUtil.copyProperties(comment, ArticleCommentVO.class);
    }

    /**
     * 批量填充评论用户昵称与头像
     */
    private void fillUserInfo(List<ArticleCommentVO> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }
        List<Long> userIds = comments.stream()
                .map(ArticleCommentVO::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        for (ArticleCommentVO comment : comments) {
            SysUser user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setNickname(user.getNickname());
                comment.setAvatar(user.getAvatar());
            }
        }
    }

    /**
     * 增加文章评论数
     *
     * @param articleId 文章 ID
     */
    private void incrementArticleCommentCount(Long articleId) {
        if (articleId == null) return;

        Articles article = articleService.getById(articleId);
        if (article != null) {
            article.setCommentCount(article.getCommentCount() != null ? article.getCommentCount() + 1 : 1);
            articleService.updateById(article);
        }
    }

    /**
     * 减少文章评论数（最小为 0）
     *
     * @param articleId 文章 ID
     */
    private void decrementArticleCommentCount(Long articleId) {
        if (articleId == null) return;

        Articles article = articleService.getById(articleId);
        if (article != null && article.getCommentCount() != null && article.getCommentCount() > 0) {
            article.setCommentCount(Math.max(0, article.getCommentCount() - 1));
            articleService.updateById(article);
        }
    }

    /**
     * 检查父评论是否开启邮箱通知，如果是则发送通知邮件
     * <p>
     * 父评论作者邮箱从 sys_user 表查询，按 user_id 关联。
     * </p>
     *
     * @param parentId 父评论 ID
     * @param replyNickname 回复者昵称
     * @param replyContent 回复内容
     * @param type 通知类型
     */
    private void notifyParentIfNeeded(Long parentId, String replyNickname, String replyContent, String type) {
        if (parentId == null) {
            return;
        }

        try {
            ArticleComments parentComment = getById(parentId);
            if (parentComment == null
                    || parentComment.getIsNotice() == null
                    || parentComment.getIsNotice() != 1) {
                return;
            }

            // 父评论作者必须存在 userId（旧匿名数据可能为 null，跳过通知）
            Long parentUserId = parentComment.getUserId();
            if (parentUserId == null) {
                return;
            }

            // 通过 sys_user 查询父评论作者的邮箱与昵称
            SysUser parentUser = sysUserMapper.selectById(parentUserId);
            if (parentUser == null
                    || parentUser.getEmail() == null
                    || parentUser.getEmail().isEmpty()) {
                return;
            }

            // 异步发送回复通知邮件
            asyncEmailService.sendReplyNotificationAsync(
                    parentUser.getEmail(),
                    parentUser.getNickname(),
                    parentComment.getContent(),
                    replyNickname,
                    replyContent,
                    type
            );

            log.info("评论回复通知已派发: parentId={}, toEmail={}", parentId, parentUser.getEmail());
        } catch (Exception e) {
            log.error("发送评论回复通知邮件异常：parentId={}, ex={}", parentId, e.getMessage());
        }
    }

    /**
     * 统计总评论数
     * @return 总评论数
     */
    @Override
    public Integer countTotal() {
        return Math.toIntExact(count());
    }

    /**
     * 统计待审核评论数
     * @return 待审核评论数
     */
    @Override
    public Integer countPending() {
        LambdaQueryWrapper<ArticleComments> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ArticleComments::getIsApproved, StatusConstant.DISABLE);
        return Math.toIntExact(count(wrapper));
    }
}