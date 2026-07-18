package com.xuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xuan.entity.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 文章-作者关联
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_authors")
@EqualsAndHashCode(callSuper = true)
public class ArticleAuthors extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 文章ID
    private Long articleId;

    // 作者ID
    private Long userId;

    // 作者角色：1第一作者 2共同作者 3通讯作者 9其他
    private Integer authorRole;

    // 是否有编辑权限：0否 1是
    private Integer canEdit;

    // 邀请状态：0待接受 1已接受 2已拒绝 3已移除
    private Integer inviteStatus;

    // 邀请人ID
    private Long invitedBy;

    // 展示排序，越小越靠前
    private Integer sort;
}
