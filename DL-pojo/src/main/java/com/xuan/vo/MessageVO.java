package com.xuan.vo;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 留言VO（树形结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 留言ID
    private Long id;

    // 根留言ID
    private Long rootId;

    // 父留言ID
    private Long parentId;

    // 回复对象昵称
    private String parentNickname;

    // 留言内容
    private String content;

    // 留言内容HTML
    private String contentHtml;

    // 是否Markdown
    private Integer isMarkdown;

    // 访客ID
    private Long visitorId;

    // 昵称
    private String nickname;

    // 邮箱或QQ
    private String emailOrQq;

    // 地区
    private String location;

    // 操作系统
    private String userAgentOs;

    // 浏览器
    private String userAgentBrowser;

    // 审核状态
    private Integer isApproved;

    // 是否私密
    private Integer isSecret;

    // 是否管理员回复
    private Integer isAdminReply;

    // 创建时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // 子留言列表（仅根留言有值）
    private List<MessageVO> children;
}
