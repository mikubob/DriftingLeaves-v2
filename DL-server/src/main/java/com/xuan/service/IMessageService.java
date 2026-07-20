package com.xuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuan.dto.MessageDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.MessagePageQueryDTO;
import com.xuan.dto.MessageReplyDTO;
import com.xuan.entity.Messages;
import com.xuan.result.PageResult;
import com.xuan.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 留言服务
 */
public interface IMessageService extends IService<Messages> {

    /**
     * 访客提交留言
     * @param messageDTO
     * @param userId
     * @param request
     */
    void submitMessage(MessageDTO messageDTO, Long userId, HttpServletRequest request);

    /**
     * 分页条件查询留言
     * @param messagePageQueryDTO
     * @return
     */
    PageResult pageQuery(MessagePageQueryDTO messagePageQueryDTO);

    /**
     * 批量审核通过留言
     * @param ids
     */
    void batchApprove(List<Long> ids);

    /**
     * 批量删除留言
     * @param ids
     */
    void batchDelete(List<Long> ids);

    /**
     * 管理员回复留言
     * @param messageReplyDTO
     */
    void adminReply(MessageReplyDTO messageReplyDTO, HttpServletRequest request);

    // ===== 博客端方法 =====

    /**
     * 获取已审核留言列表（树形结构）
     */
    List<MessageVO> getMessageTree(Long userId);

    /**
     * 用户编辑留言
     */
    void editMessage(MessageEditDTO editDTO, Long userId);

    /**
     * 用户删除留言
     */
    void visitorDeleteMessage(Long id, Long userId);

    /**
     * 统计总留言数
     * @return 总留言数
     */
    Integer countTotal();

    /**
     * 统计待审核留言数
     * @return 待审核留言数
     */
    Integer countPending();
}
