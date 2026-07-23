package com.xuan.controller.admin;

import com.xuan.annotation.OperationLog;
import com.xuan.dto.MessagePageQueryDTO;
import com.xuan.dto.MessageReplyDTO;
import com.xuan.enumeration.OperationType;
import com.xuan.result.PageResult;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端留言接口
 * <p>
 * 类级 @PreAuthorize：仅 ADMIN + AUDITOR 可访问。AUTHOR 角色被排除（仅能操作文章模块）。
 * 写操作方法（POST/PUT/DELETE）在方法级再追加 @PreAuthorize("hasRole('ADMIN')") 排除 AUDITOR。
 * </p>
 */
@Slf4j
@RestController("adminMessageController")
@RequestMapping("/admin/message")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class MessageController {

    private final IMessageService messageService;

    /**
     * 分页条件查询留言
     * @param messagePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> pageQuery(MessagePageQueryDTO messagePageQueryDTO) {
        log.info("分页条件查询留言: {}", messagePageQueryDTO);
        PageResult pageResult = messageService.pageQuery(messagePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量审核通过留言
     * @param ids
     * @return
     */
    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.UPDATE, target = "message", targetId = "#ids")
    public Result<String> batchApprove(@RequestParam List<Long> ids) {
        log.info("批量审核通过留言: {}", ids);
        messageService.batchApprove(ids);
        return Result.success();
    }

    /**
     * 批量删除留言
     * @param ids
     * @return
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.DELETE, target = "message", targetId = "#ids")
    public Result<String> batchDelete(@RequestParam List<Long> ids) {
        log.info("批量删除留言: {}", ids);
        messageService.batchDelete(ids);
        return Result.success();
    }

    /**
     * 管理员回复留言
     * @param messageReplyDTO
     * @return
     */
    @PostMapping("/reply")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(value = OperationType.INSERT, target = "message", targetId = "#messageReplyDTO.parentId")
    public Result<String> adminReply(@Valid @RequestBody MessageReplyDTO messageReplyDTO,
                                     HttpServletRequest request) {
        log.info("管理员回复留言: {}", messageReplyDTO);
        messageService.adminReply(messageReplyDTO, request);
        return Result.success();
    }
}
