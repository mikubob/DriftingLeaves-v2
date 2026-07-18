package com.xuan.service.impl;

import com.xuan.service.AsyncEmailService;
import com.xuan.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步邮件服务实现类(独立Service 确保 @Async 代理生效)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncEmailServiceImpl implements AsyncEmailService {

    private final EmailService emailService;

    /**
     * 异步发送评论/留言恢复通知邮件
     *
     * @param toEmail        接收者邮箱
     * @param parentNickname 父级昵称
     * @param parentContent  父级内容
     * @param replyNickname  回复者昵称
     * @param replyContent   回复内容
     * @param type           通知类型
     */
    @Override
    @Async("taskExecutor")
    public void sendReplyNotificationAsync(String toEmail, String parentNickname, String parentContent, String replyNickname, String replyContent, String type) {
        try {
            emailService.sendReplyNotification(
                    toEmail
                    , parentNickname
                    , parentContent
                    , replyNickname
                    , replyContent
                    , type
            );
        } catch (Exception e){
            log.error("异步发送回复通知邮件失败: to={}, type={}, ex={}", toEmail, type, e.getMessage());
        }
    }

    /**
     * 异步发送新文章通知邮件
     * @param toEmail 接收者邮箱
     * @param nickname 昵称
     * @param articleTitle 文章标题
     * @param articleSummary 文章摘要
     * @param articleUrl 文章链接
     */
    @Override
    @Async("taskExecutor")
    public void sendNewArticleNotificationAsync(String toEmail, String nickname, String articleTitle, String articleSummary, String articleUrl) {
        try {
            emailService.sendNewArticleNotification(
                    toEmail
                    , nickname
                    , articleTitle
                    , articleSummary
                    , articleUrl
            );
        } catch (Exception e){
            log.error("异步发送新文章通知邮件失败: to={}, title={}, ex={}", toEmail, articleTitle, e.getMessage());
        }
    }
}
