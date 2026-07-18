package com.xuan.utils;

import com.xuan.properties.WebsiteProperties;

import java.time.LocalDate;

/**
 * 邮件模板工具类
 * 提供各种邮件模板的构建方法
 */
public class EmailTemplateUtil {

    /**
     * 构建发送验证码的邮件内容
     * @param code 验证码
     * @param websiteProperties 网站配置
     * @return 邮件内容
     */
    public static String buildSendVerifyCodeEmailContent(String code, WebsiteProperties websiteProperties) {
        String year = String.valueOf(LocalDate.now().getYear());
        return "<!DOCTYPE html>" +
                "<html lang='zh-CN'>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>"+ websiteProperties.getTitle() +"验证码</title>" +
                "    <style>" +
                "        body {" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            font-family: \"PingFang SC\", \"Microsoft YaHei\", sans-serif;" +
                "            background-color: #f5f5f5;" +
                "        }" +
                "        .email-container {" +
                "            max-width: 600px;" +
                "            margin: 40px auto;" +
                "            background: white;" +
                "            border-radius: 8px;" +
                "            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);" +
                "            overflow: hidden;" +
                "        }" +
                "        .email-header {" +
                "            background: #333;" +
                "            padding: 24px 0;" +
                "            text-align: center;" +
                "            border-bottom: 1px solid #222;" +
                "        }" +
                "        .email-content {" +
                "            padding: 36px 30px;" +
                "        }" +
                "        .verification-code {" +
                "            background: #f9f9f9;" +
                "            border-radius: 8px;" +
                "            padding: 18px;" +
                "            text-align: center;" +
                "            margin: 26px 0;" +
                "            border: 1px solid #ddd;" +
                "        }" +
                "        .code-display {" +
                "            display: inline-block;" +
                "            background: white;" +
                "            padding: 12px 24px;" +
                "            border-radius: 6px;" +
                "            border: 1px solid #333;" +
                "            margin: 10px 0;" +
                "        }" +
                "        .footer {" +
                "            background: #222;" +
                "            padding: 16px;" +
                "            text-align: center;" +
                "            color: #aaa;" +
                "            font-size: 12px;" +
                "        }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='email-container'>" +
                "        <div class='email-header'>" +
                "            <h1 style='color: white; margin: 0; font-size: 24px; font-weight: 500;'>"+websiteProperties.getTitle()+"</h1>" +
                "            <p style='color: #bbb; margin: 8px 0 0; font-size: 14px;'>—— 邮箱验证 ——</p>" +
                "        </div>" +
                "        " +
                "        <div class='email-content'>" +
                "            <h2 style='color: #333; margin: 0 0 20px; font-size: 20px; font-weight: 500;'>您好！</h2>" +
                "            <p style='color: #555; font-size: 15px; line-height: 1.6; margin: 0 0 25px;'>" +
                "                请使用以下验证码完成邮箱验证：" +
                "            </p>" +
                "            " +
                "            <div class='verification-code'>" +
                "                <p style='color: #666; margin: 0 0 12px; font-size: 14px;'>验证码</p>" +
                "                <div class='code-display'>" +
                "                    <span style='color: #333; font-size: 28px; font-weight: bold; letter-spacing: 6px; font-family: \"Courier New\", monospace;'>" + code + "</span>" +
                "                </div>" +
                "                <p style='color: #888; margin: 12px 0 0; font-size: 13px; font-weight: 500;'>有效期 5 分钟，请及时使用</p>" +
                "            </div>" +
                "        </div>" +
                "        " +
                "        <div class='footer'>" +
                "            <p style='margin: 0; line-height: 1.5;'>" +
                "                此为系统自动发送的邮件，请勿直接回复<br>" +
                "                © "+ year +" "+ websiteProperties.getTitle() +" - 保留所有权利" +
                "            </p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * 构建回复通知邮件内容
     */
    public static String buildReplyNotificationEmailContent(String parentNickname, String parentContent,
                                                      String replyNickname, String replyContent, String typeText, WebsiteProperties websiteProperties) {
        String year = String.valueOf(LocalDate.now().getYear());
        return "<!DOCTYPE html>" +
                "<html lang='zh-CN'>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>"+ websiteProperties.getTitle() +"回复通知</title>" +
                "    <style>" +
                "        body { margin: 0; padding: 0; font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; background-color: #f5f5f5; }" +
                "        .email-container { max-width: 600px; margin: 40px auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }" +
                "        .email-header { background: #333; padding: 24px 0; text-align: center; border-bottom: 1px solid #222; }" +
                "        .email-content { padding: 36px 30px; }" +
                "        .quote-block { background: #f9f9f9; border-left: 4px solid #333; padding: 16px 20px; margin: 16px 0; border-radius: 0 8px 8px 0; }" +
                "        .reply-block { background: #f0f7ff; border-left: 4px solid #4a90d9; padding: 16px 20px; margin: 16px 0; border-radius: 0 8px 8px 0; }" +
                "        .footer { background: #222; padding: 16px; text-align: center; color: #aaa; font-size: 12px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='email-container'>" +
                "        <div class='email-header'>" +
                "            <h1 style='color: white; margin: 0; font-size: 24px; font-weight: 500;'>"+websiteProperties.getTitle()+"</h1>" +
                "            <p style='color: #bbb; margin: 8px 0 0; font-size: 14px;'>—— " + typeText + "回复通知 ——</p>" +
                "        </div>" +
                "        <div class='email-content'>" +
                "            <h2 style='color: #333; margin: 0 0 20px; font-size: 20px; font-weight: 500;'>" + parentNickname + "，您好！</h2>" +
                "            <p style='color: #555; font-size: 15px; line-height: 1.6; margin: 0 0 16px;'>您的" + typeText + "收到了来自 <strong>" + replyNickname + "</strong> 的回复：</p>" +
                "            <div class='quote-block'>" +
                "                <p style='color: #888; margin: 0 0 6px; font-size: 13px;'>您的原始内容：</p>" +
                "                <p style='color: #555; margin: 0; font-size: 14px; line-height: 1.6;'>" + parentContent + "</p>" +
                "            </div>" +
                "            <div class='reply-block'>" +
                "                <p style='color: #666; margin: 0 0 6px; font-size: 13px;'>" + replyNickname + " 的回复：</p>" +
                "                <p style='color: #333; margin: 0; font-size: 14px; line-height: 1.6;'>" + replyContent + "</p>" +
                "            </div>" +
                "        </div>" +
                "        <div class='footer'>" +
                "            <p style='margin: 0; line-height: 1.5;'>此为系统自动发送的邮件，请勿直接回复<br>© " + year + " "+websiteProperties.getTitle()+" - 保留所有权利</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * 构建新文章通知邮件内容
     */
    public static String buildNewArticleNotificationEmailContent(String nickname, String articleTitle,
                                                           String articleSummary, String articleUrl, WebsiteProperties websiteProperties) {
        String year = String.valueOf(LocalDate.now().getYear());
        return "<!DOCTYPE html>" +
                "<html lang='zh-CN'>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>"+websiteProperties.getTitle()+"新文章通知</title>" +
                "    <style>" +
                "        body { margin: 0; padding: 0; font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; background-color: #f5f5f5; }" +
                "        .email-container { max-width: 600px; margin: 40px auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }" +
                "        .email-header { background: #333; padding: 24px 0; text-align: center; border-bottom: 1px solid #222; }" +
                "        .email-content { padding: 36px 30px; }" +
                "        .article-card { background: #f9f9f9; border-radius: 8px; padding: 20px 24px; margin: 20px 0; border: 1px solid #eee; }" +
                "        .read-btn { display: inline-block; background: #333; color: white; padding: 12px 28px; border-radius: 6px; text-decoration: none; font-size: 15px; font-weight: 500; margin-top: 10px; }" +
                "        .footer { background: #222; padding: 16px; text-align: center; color: #aaa; font-size: 12px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='email-container'>" +
                "        <div class='email-header'>" +
                "            <h1 style='color: white; margin: 0; font-size: 24px; font-weight: 500;'>"+websiteProperties.getTitle()+"</h1>" +
                "            <p style='color: #bbb; margin: 8px 0 0; font-size: 14px;'>—— 新文章发布通知 ——</p>" +
                "        </div>" +
                "        <div class='email-content'>" +
                "            <h2 style='color: #333; margin: 0 0 20px; font-size: 20px; font-weight: 500;'>" + nickname + "，您好！</h2>" +
                "            <p style='color: #555; font-size: 15px; line-height: 1.6; margin: 0 0 16px;'>您订阅的博客有新文章发布了：</p>" +
                "            <div class='article-card'>" +
                "                <h3 style='color: #333; margin: 0 0 12px; font-size: 18px; font-weight: 600;'>" + articleTitle + "</h3>" +
                "                <p style='color: #666; margin: 0 0 16px; font-size: 14px; line-height: 1.6;'>" + (articleSummary != null ? articleSummary : "") + "</p>" +
                "                <a href='" + articleUrl + "' class='read-btn' style='color: white;'>阅读全文 →</a>" +
                "            </div>" +
                "        </div>" +
                "        <div class='footer'>" +
                "            <p style='margin: 0; line-height: 1.5;'>此为系统自动发送的邮件，请勿直接回复<br>© " + year + " "+websiteProperties.getTitle()+" - 保留所有权利</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }
}