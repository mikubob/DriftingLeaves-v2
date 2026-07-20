package com.xuan.controller.blog;

import com.xuan.annotation.RateLimit;
import com.xuan.auth.security.SecurityUser;
import com.xuan.dto.MessageDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.result.Result;
import com.xuan.service.IMessageService;
import com.xuan.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 博客端留言接口
 * <p>
 * 阶段四：写接口（POST/PUT/DELETE）需 GUEST 角色（由 ResourceServerConfig 控制），
 * userId 由 {@link SecurityUser} 中获取，不再由前端传入。
 * </p>
 */
@RestController("blogMessageController")
@RequestMapping("/blog/message")
@Slf4j
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messageService;

    /**
     * 用户提交留言
     */
    @PostMapping
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
              timeWindow = 60, message = "留言过于频繁，请稍后再试")
    public Result<String> submitMessage(@Valid @RequestBody MessageDTO messageDTO,
                                        @AuthenticationPrincipal SecurityUser securityUser,
                                        HttpServletRequest request) {
        Long userId = securityUser.getUserId();
        log.info("用户提交留言: userId={}, dto={}", userId, messageDTO);
        messageService.submitMessage(messageDTO, userId, request);
        return Result.success();
    }

    /**
     * 获取留言列表（树形结构，含当前用户的未审核留言）
     * <p>GET 接口公开访问，未登录时 userId 为 null。</p>
     */
    @GetMapping
    public Result<List<MessageVO>> getMessageTree(@AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser != null ? securityUser.getUserId() : null;
        log.info("博客端获取留言树: userId={}", userId);
        List<MessageVO> messageTree = messageService.getMessageTree(userId);
        return Result.success(messageTree);
    }

    /**
     * 用户编辑留言
     */
    @PutMapping("/edit")
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
              timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result<String> editMessage(@Valid @RequestBody MessageEditDTO editDTO,
                                      @AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser.getUserId();
        log.info("用户编辑留言: userId={}, dto={}", userId, editDTO);
        messageService.editMessage(editDTO, userId);
        return Result.success();
    }

    /**
     * 用户删除留言
     */
    @DeleteMapping("/{id}")
    @RateLimit(type = RateLimit.Type.IP, tokens = 5, burstCapacity = 8,
              timeWindow = 60, message = "操作过于频繁，请稍后再试")
    public Result<String> deleteMessage(@PathVariable Long id,
                                        @AuthenticationPrincipal SecurityUser securityUser) {
        Long userId = securityUser.getUserId();
        log.info("用户删除留言: id={}, userId={}", id, userId);
        messageService.visitorDeleteMessage(id, userId);
        return Result.success();
    }
}
