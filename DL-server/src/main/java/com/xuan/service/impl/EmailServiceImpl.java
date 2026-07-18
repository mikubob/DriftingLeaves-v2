package com.xuan.service.impl;

import com.xuan.constant.MessageConstant;
import com.xuan.exception.EmailSendErrorException;
import com.xuan.properties.EmailProperties;
import com.xuan.properties.WebsiteProperties;
import com.xuan.service.EmailService;
import com.xuan.utils.EmailTemplateUtil;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final WebsiteProperties websiteProperties;

    /**
     * 发送验证码
     * @param toEmail 接收方邮箱
     * @param code 验证码
     */
    @Override
    public void sendVerifyCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailProperties.getFrom(), emailProperties.getPersonal());
            helper.setTo(toEmail);
            helper.setSubject("【" + websiteProperties.getTitle() + "】管理端-验证码");
            helper.setText(EmailTemplateUtil.buildSendVerifyCodeEmailContent(code, websiteProperties), true);
            mailSender.send(message);
        } catch (Exception e){
            log.error("发送验证码邮件失败 to={}, ex={}", toEmail, e.getMessage());
            throw new EmailSendErrorException(MessageConstant.EMAIL_SEND_ERROR);
        }
    }

    /**
     * 发送回复通知
     * @param toEmail 收件人邮箱
     * @param parentNickname 被回复人昵称
     * @param parentContent 被回复的内容
     * @param replyNickname 回复人昵称
     * @param replyContent 回复内容
     * @param type 类型：message-留言 / comment-文章评论
     */
    @Override
    public void sendReplyNotification(String toEmail, String parentNickname, String parentContent, String replyNickname, String replyContent, String type) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailProperties.getFrom(), emailProperties.getPersonal());
            helper.setTo(toEmail);
            String typeText = "comment".equals(type) ? "文章评论" : "留言";
            helper.setSubject(websiteProperties.getTitle() + " - 您的" + typeText + "收到了新回复");
            helper.setText(EmailTemplateUtil.buildReplyNotificationEmailContent(parentNickname, parentContent,
                    replyNickname, replyContent, typeText, websiteProperties), true);
            mailSender.send(message);
            log.info("发送回复通知邮件成功: to={}, type={}", toEmail, type);
        } catch (Exception e) {
            log.error("发送回复通知邮件失败 to={}, type={}, ex={}", toEmail, type, e.getMessage());
        }
    }

    /**
     * 发送新文章通知
     * @param toEmail 收件人邮箱
     * @param nickname 订阅者昵称
     * @param articleTitle 文章标题
     * @param articleSummary 文章摘要
     * @param articleUrl 文章链接
     */
    @Override
    public void sendNewArticleNotification(String toEmail, String nickname, String articleTitle, String articleSummary, String articleUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailProperties.getFrom(), emailProperties.getPersonal());
            helper.setTo(toEmail);
            helper.setSubject(websiteProperties.getTitle() + " - 新文章发布：" + articleTitle);
            helper.setText(EmailTemplateUtil.buildNewArticleNotificationEmailContent(nickname, articleTitle, articleSummary, articleUrl, websiteProperties), true);
            mailSender.send(message);
            log.info("发送新文章通知邮件成功: to={}, title={}", toEmail, articleTitle);
        } catch (Exception e) {
            log.error("发送新文章通知邮件失败 to={}, title={}, ex={}", toEmail, articleTitle, e.getMessage());
        }
    }
}
