package com.xuan.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章审核记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_audit_logs")
public class ArticleAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 文章ID
    private Long articleId;

    // 操作人ID
    private Long operatorId;

    // 变更前状态：0草稿 1待审核 2已发布 3违规
    private Integer fromStatus;

    // 变更后状态：0草稿 1待审核 2已发布 3违规
    private Integer toStatus;

    // 审核意见/违规原因/操作说明
    private String reason;

    // 操作IP
    private String ipAddress;

    // 创建时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
