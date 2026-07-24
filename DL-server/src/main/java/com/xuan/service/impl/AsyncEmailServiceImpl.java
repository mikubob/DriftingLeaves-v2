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
     * @param parentUsername 父级用户名
     * @param parentContent  父级内容
     * @param replyUsername  回复者用户名
     * @param replyContent   回复内容
     * @param type           通知类型
     */
    @Override
    @Async("taskExecutor")
    public void sendReplyNotificationAsync(String toEmail, String parentUsername, String parentContent, String replyUsername, String replyContent, String type) {
        try {
            emailService.sendReplyNotification(
                    toEmail
                    , parentUsername
                    , parentContent
                    , replyUsername
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
     * @param username 用户名
     * @param articleTitle 文章标题
     * @param articleSummary 文章摘要
     * @param articleUrl 文章链接
     */
    @Override
    @Async("taskExecutor")
    public void sendNewArticleNotificationAsync(String toEmail, String username, String articleTitle, String articleSummary, String articleUrl) {
        try {
            emailService.sendNewArticleNotification(
                    toEmail
                    , username
                    , articleTitle
                    , articleSummary
                    , articleUrl
            );
        } catch (Exception e){
            log.error("异步发送新文章通知邮件失败: to={}, title={}, ex={}", toEmail, articleTitle, e.getMessage());
        }
    }

    /**
     * 异步发送邮箱验证码邮件（博客端注册/登录使用）
     *
     * @param toEmail 收件人邮箱
     * @param code    验证码
     */
    @Override
    @Async("taskExecutor")
    public void sendVerifyCodeAsync(String toEmail, String code) {
        try {
            emailService.sendVerifyCode(toEmail, code);
        } catch (Exception e) {
            log.error("异步发送邮箱验证码邮件失败: to={}, ex={}", toEmail, e.getMessage());
        }
    }
}
