package com.xuan.service;

/**
 * 异步邮件服务（通过独立 Service 确保 @Async 代理生效）
 */
public interface AsyncEmailService {

    /**
     * 异步发送评论/留言回复通知邮件
     */
    void sendReplyNotificationAsync(String toEmail, String parentUsername, String parentContent,
                                    String replyUsername, String replyContent, String type);

    /**
     * 异步发送新文章通知邮件
     */
    void sendNewArticleNotificationAsync(String toEmail, String username, String articleTitle,
                                         String articleSummary, String articleUrl);

    /**
     * 异步发送邮箱验证码邮件（博客端注册/登录使用）
     *
     * @param toEmail 收件人邮箱
     * @param code    验证码
     */
    void sendVerifyCodeAsync(String toEmail, String code);
}
