package com.xuan.controller.admin;


import com.xuan.annotation.OperationLog;
import com.xuan.dto.ArticleDTO;
import com.xuan.dto.ArticlePageQueryDTO;
import com.xuan.entity.Articles;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.ArticlePermissionService;
import com.xuan.service.IArticleService;
import com.xuan.vo.ArticleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章接口
 * <p>
 * RBAC 权限模型第三层（方法级 @PreAuthorize）的核心落地示例。
 * </p>
 *
 * <h3>权限策略</h3>
 * <ul>
 *     <li>读操作（GET）：ADMIN + AUTHOR + AUDITOR 可访问（GUEST 排除，URL 层已拦截）</li>
 *     <li>写操作（POST/PUT/DELETE）：ADMIN + AUTHOR 可访问（AUDITOR 排除，URL 层已拦截）</li>
 *     <li>数据范围：AUTHOR 仅能操作自己的文章，通过 @articlePermissionService SpEL 调用校验</li>
 *     <li>ADMIN 专属：置顶、审核（SpEL 短路求值，ADMIN 不调用 ArticlePermissionService）</li>
 * </ul>
 *
 * <h3>SpEL 短路求值说明</h3>
 * <pre>
 * hasRole('ADMIN') or (hasRole('AUTHOR') and @articlePermissionService.isAuthor(...))
 *   ├─ 当前用户是 ADMIN → hasRole('ADMIN')=true → 短路，直接放行
 *   ├─ 当前用户是 AUTHOR → hasRole('ADMIN')=false → 求值右侧
 *   │   ├─ hasRole('AUTHOR')=true → 调用 @articlePermissionService.isAuthor(...)
 *   │   └─ isAuthor=true → 放行；isAuthor=false → 403
 *   └─ 当前用户是 AUDITOR/GUEST → hasRole('ADMIN')=false, hasRole('AUTHOR')=false → 403
 * </pre>
 *
 * <h3>审核端点拆分说明</h3>
 * <p>
 * 原 updateStatus 同时承载"作者提交审核"与"管理员审核"两种语义，权限要求不同。
 * 将其拆分为：
 * <ul>
 *     <li>PUT /admin/article/status/{id}：作者提交审核/撤回（AUTHOR + ADMIN）</li>
 *     <li>PUT /admin/article/audit/{id}：管理员审核通过/违规（仅 ADMIN）</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController("adminArticleController")
@RequestMapping("/admin/article")
@RequiredArgsConstructor
public class ArticleController {

    private final IArticleService articleService;

    /**
     * 文章权限校验服务
     * <p>
     * 在方法级 @PreAuthorize SpEL 表达式中通过 {@code @articlePermissionService.xxx()} 调用，
     * 用于校验 AUTHOR 是否只能操作自己的文章。Bean 名称由 Spring 自动推断为 articlePermissionServiceImpl。
     * </p>
     */
    private final ArticlePermissionService articlePermissionService;

    /**
     * 分页条件查询文章列表
     * <p>
     * 权限：ADMIN + AUTHOR + AUDITOR 均可查询。
     * AUDITOR 看全量；AUTHOR 看自己的（Service 层做数据范围过滤）。
     * </p>
     * @param articlePageQueryDTO 查询条件
     * @return 文章分页列表
     */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<PageResult<ArticleVO>> pageQuery(ArticlePageQueryDTO articlePageQueryDTO) {
        log.info("分页条件查询文章列表: {}", articlePageQueryDTO);
        // 返回 ArticleVO，确保管理列表可以直接拿到 categoryName 等展示字段。
        PageResult<ArticleVO> pageResult = articleService.pageQuery(articlePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据 ID 获取文章详情
     * <p>
     * 权限：ADMIN + AUTHOR + AUDITOR 均可查询。
     * AUTHOR 在 Service 层校验文章归属（非自己文章返回 403 或空）。
     * </p>
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<Articles> getArticleById(@PathVariable Long id) {
        log.info("根据 ID 获取文章详情：{}", id);
        Articles articles = articleService.getArticleById(id);
        return Result.success(articles);
    }

    /**
     * 创建文章
     * <p>
     * 权限：ADMIN + AUTHOR。AUDITOR/GUEST 无创建权限。
     * 创建后由 Service 层将当前用户写入 article_authors 表作为第一作者。
     * </p>
     * @param articleDTO 文章数据
     * @return 操作结果
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR')")
    @OperationLog(value = OperationType.INSERT, target = "article")
    public Result createArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        log.info("创建文章: {}", articleDTO);
        articleService.createArticle(articleDTO);
        return Result.success();
    }

    /**
     * 更新文章
     * <p>
     * 权限：ADMIN 或（AUTHOR 且为该文章作者）。
     * SpEL 短路求值：ADMIN 直接通过，不调用 ArticlePermissionService；
     * AUTHOR 才调用 {@link ArticlePermissionService#isAuthor} 校验是否为该文章的作者（含共同作者）。
     * </p>
     * @param articleDTO 文章数据（含 id）
     * @return 操作结果
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or " +
                  "(hasRole('AUTHOR') and @articlePermissionService.isAuthor(#articleDTO.id, authentication.name))")
    @OperationLog(value = OperationType.UPDATE, target = "article", targetId = "#articleDTO.id")
    public Result updateArticle(@Valid @RequestBody ArticleDTO articleDTO) {
        log.info("更新文章: {}", articleDTO);
        articleService.updateArticle(articleDTO);
        return Result.success();
    }

    /**
     * 批量删除文章
     * <p>
     * 权限：ADMIN 或（AUTHOR 且为所有指定文章的第一作者）。
     * SpEL 表达式调用 {@link ArticlePermissionService#isFirstAuthor(List, String)} 批量校验。
     * </p>
     * <p>
     * 设计说明：删除是高权限操作，仅第一作者可删除（共同作者不能删除）。
     * </p>
     * @param ids 文章ID列表
     * @return 操作结果
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or " +
                  "(hasRole('AUTHOR') and @articlePermissionService.isFirstAuthor(#ids, authentication.name))")
    @OperationLog(value = OperationType.DELETE, target = "article", targetId = "#ids")
    public Result batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除文章: {}", ids);
        articleService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 更新文章状态（作者提交审核/撤回）
     * <p>
     * 权限：ADMIN 或（AUTHOR 且为该文章作者）。
     * </p>
     * <p>
     * 适用状态流转：
     * <ul>
     *     <li>0 草稿 ↔ 1 待审核（作者提交审核 / 撤回审核）</li>
     *     <li>1 待审核 → 0 草稿（作者撤回）</li>
     * </ul>
     * </p>
     * <p>
     * 注：管理员审核（1 待审核 → 2 已发布 / 3 违规）请使用独立端点
     * {@link #auditArticle(Long, Integer)}（PUT /admin/article/audit/{id}）。
     * </p>
     * @param id 文章ID
     * @param status 文章状态：0草稿 1待审核
     * @return 操作结果
     */
    @PutMapping("/status/{id}")
    @PreAuthorize("hasRole('ADMIN') or " +
                  "(hasRole('AUTHOR') and @articlePermissionService.isAuthor(#id, authentication.name))")
    @OperationLog(value = OperationType.UPDATE, target = "article", targetId = "#id")
    public Result updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        log.info("更新文章状态（作者提交/撤回审核）: id={}, status={}", id, status);
        articleService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 管理员审核文章（独立端点，仅 ADMIN）
     * <p>
     * 权限：仅 ADMIN。AUTHOR 不能审核自己的文章（避免自审）。
     * </p>
     * <p>
     * 适用状态流转：
     * <ul>
     *     <li>1 待审核 → 2 已发布（审核通过）</li>
     *     <li>1 待审核 → 3 违规（审核不通过）</li>
     *     <li>2 已发布 → 3 违规（已发布后判定违规）</li>
     *     <li>3 违规 → 2 已发布（恢复发布）</li>
     * </ul>
     * </p>
     * <p>
     * 拆分独立端点的原因：
     * <ul>
     *     <li>原 updateStatus 端点同时承载"作者提交审核"和"管理员审核"两种语义</li>
     *     <li>权限要求不同：作者提交=AUTHOR+ADMIN，管理员审核=仅 ADMIN</li>
     *     <li>独立端点让权限边界更清晰，便于审计与权限矩阵管理</li>
     * </ul>
     * </p>
     * @param id 文章ID
     * @param status 目标状态：2已发布 3违规
     * @return 操作结果
     */
    @PutMapping("/audit/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "articleAudit", targetId = "#id")
    public Result auditArticle(@PathVariable Long id, @RequestParam Integer status) {
        log.info("管理员审核文章: id={}, status={}", id, status);
        articleService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 置顶/取消置顶文章
     * <p>
     * 权限：仅 ADMIN。置顶是运营操作，AUTHOR 不能置顶自己的文章。
     * </p>
     * @param id 文章ID
     * @param isTop 0-取消置顶，1-置顶
     * @return 操作结果
     */
    @PutMapping("/top/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "articleTop", targetId = "#id")
    public Result toggleTop(@PathVariable Long id, @RequestParam Integer isTop) {
        log.info("置顶/取消置顶文章: id={}, isTop={}", id, isTop);
        articleService.toggleTop(id, isTop);
        return Result.success();
    }

    /**
     * 文章搜索（全文本搜索：标题、摘要、内容）
     * <p>
     * 权限：ADMIN + AUTHOR + AUDITOR 均可搜索。
     * AUTHOR 在 Service 层仅返回自己文章的搜索结果。
     * </p>
     * @param keyword 搜索关键词
     * @param page 页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUTHOR', 'AUDITOR')")
    public Result<PageResult> search(@RequestParam String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        log.info("文章搜索：keyword={}", keyword);
        PageResult pageResult = articleService.search(keyword, page, pageSize);
        return Result.success(pageResult);
    }
}
