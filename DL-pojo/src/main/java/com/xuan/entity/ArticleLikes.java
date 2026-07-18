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
 * 文章点赞
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_likes")
public class ArticleLikes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // 文章ID
    private Long articleId;

    // 点赞用户ID
    private Long userId;

    // 点赞时间
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime likeTime;
}
