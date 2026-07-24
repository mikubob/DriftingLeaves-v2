package com.xuan.service;

/**
 * 邮件服务
 */
public interface EmailService {
    
    /**
     * 发送验证码邮件
     * @param toEmail
     * @param code
     */
    void sendVerifyCode(String toEmail, String code);

    /**
     * 发送评论/留言回复通知邮件
     * @param toEmail 收件人邮箱
     * @param parentUsername 被回复人用户名
     * @param parentContent 被回复的内容
     * @param replyUsername 回复人用户名
     * @param replyContent 回复内容
     * @param type 类型：message-留言 / comment-文章评论
     */
    void sendReplyNotification(String toEmail, String parentUsername, String parentContent,
                               String replyUsername, String replyContent, String type);

    /**
     * 发送新文章通知邮件
     * @param toEmail 收件人邮箱
     * @param username 订阅者用户名
     * @param articleTitle 文章标题
     * @param articleSummary 文章摘要
     * @param articleUrl 文章链接
     */
    void sendNewArticleNotification(String toEmail, String username, String articleTitle,
                                    String articleSummary, String articleUrl);
}
