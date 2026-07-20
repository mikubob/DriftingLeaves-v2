package com.xuan.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.MessageConstant;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.MessageDTO;
import com.xuan.dto.MessageEditDTO;
import com.xuan.dto.MessagePageQueryDTO;
import com.xuan.dto.MessageReplyDTO;
import com.xuan.entity.Messages;
import com.xuan.entity.SysUser;
import com.xuan.exception.ValidationException;
import com.xuan.mapper.MessageMapper;
import com.xuan.mapper.SysUserMapper;
import com.xuan.properties.WebsiteProperties;
import com.xuan.result.PageResult;
import com.xuan.service.AsyncEmailService;
import com.xuan.service.IMessageService;
import com.xuan.service.UserAgentService;
import com.xuan.utils.IpUtil;
import com.xuan.utils.MarkdownUtil;
import com.xuan.vo.MessageQueryVO;
import com.xuan.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 留言服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Messages> implements IMessageService {

    private final UserAgentService userAgentService;
    private final AsyncEmailService asyncEmailService;
    private final WebsiteProperties websiteProperties;
    private final SysUserMapper sysUserMapper;

    /**
     * 提交留言
     *
     * @param messageDTO 留言数据
     * @param userId     当前登录用户 ID（由 Controller 从 SecurityContext 取出）
     * @param request    请求对象
     */
    @Override
    public void submitMessage(MessageDTO messageDTO, Long userId, HttpServletRequest request) {
        //1.创建留言对象
        Messages messages = BeanUtil.copyProperties(messageDTO, Messages.class);
        //1.1 显式设置 userId（DTO 已移除该字段）
        messages.setUserId(userId);
        //2.处理Markdown内容
        if (messageDTO.getIsMarkdown() != null && messageDTO.getIsMarkdown() == 1) {
            // 如果是Markdown，转换为HTML
            String html = MarkdownUtil.toHtml(messageDTO.getContent());
            messages.setContentHtml(html);
        } else {
            // 如果不是Markdown，直接使用原内容
            messages.setContentHtml(messageDTO.getContent());
        }

        //5.获取ip地址信息
        String clientIp = IpUtil.getClientIp(request);
        Map<String, String> geoInfo = IpUtil.getGeoInfo(clientIp);

        //拼接地址：省份-城市
        String province = geoInfo.getOrDefault("province", "");
        String city = geoInfo.getOrDefault("city", "");
        String location = province.isEmpty() && city.isEmpty() ? null
                : province.equals(city) ? province
                : String.format("%s-%s", province, city).replaceAll("^-|-$", "");
        if (location != null && !location.isEmpty()) {
            messages.setLocation(location);
        }

        //6.解析UserAgent
        String userAgent = request.getHeader("User-Agent");
        String osName = userAgentService.getOsName(userAgent);
        String browserName = userAgentService.getBrowserName(userAgent);
        messages.setUserAgentOs(osName);
        messages.setUserAgentBrowser(browserName);

        //7.设置默认值
        messages.setIsApproved(StatusConstant.DISABLE);
        messages.setIsEdited(StatusConstant.DISABLE);
        messages.setCreateTime(LocalDateTime.now());
        messages.setUpdateTime(LocalDateTime.now());

        //8.保存到数据库
        save(messages);

        //9.检查父留言是否开启邮箱通知
        if (messageDTO.getParentId() != null) {
            notifyParentIfNeeded(messageDTO.getParentId(), "",
                    messageDTO.getContent(), "message");
        }

        log.info("用户提交留言成功：{}", messages);
    }

    /**
     * 分页查询留言
     *
     * @param messagePageQueryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(MessagePageQueryDTO messagePageQueryDTO) {
        //1.构建查询条件
        LambdaQueryWrapper<Messages> queryWrapper = new LambdaQueryWrapper<>();

        // 是否审核通过
        if (messagePageQueryDTO.getIsApproved() != null) {
            queryWrapper.eq(Messages::getIsApproved, messagePageQueryDTO.getIsApproved());
        }

        // 开始时间
        if (messagePageQueryDTO.getStartTime() != null) {
            queryWrapper.ge(Messages::getCreateTime, messagePageQueryDTO.getStartTime());
        }

        // 结束时间
        if (messagePageQueryDTO.getEndTime() != null) {
            queryWrapper.le(Messages::getCreateTime, messagePageQueryDTO.getEndTime());
        }

        // 按创建时间倒序排序
        queryWrapper.orderByDesc(Messages::getCreateTime);

        //2.执行分页查询
        Page<Messages> page = new Page<>(messagePageQueryDTO.getPage(), messagePageQueryDTO.getPageSize());
        page(page, queryWrapper);

        //3.转换为 QueryVO 并返回
        Page<MessageQueryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(msg -> BeanUtil.copyProperties(msg, MessageQueryVO.class))
                .toList());
        return PageResult.fromIPage(voPage);
    }

    /**
     * 批量审核通过留言
     *
     * @param ids 留言id列表
     */
    @Override
    public void batchApprove(List<Long> ids) {
        //1.构建更新条件
        LambdaUpdateWrapper<Messages> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Messages::getId, ids)
                .set(Messages::getIsApproved, StatusConstant.ENABLE)
                .set(Messages::getUpdateTime, LocalDateTime.now());
        //2.执行批量更新
        update(updateWrapper);
    }

    /**
     * 批量删除留言
     *
     * @param ids 留言id列表
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        //1.如果是根留言，级联删除所有子留言
        for (Long id : ids) {
            Messages message = getById(id);
            if (message != null && (message.getRootId() == null || message.getRootId() == 0)) {
                LambdaQueryWrapper<Messages> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Messages::getRootId, id);
                long childCount = count(wrapper);
                if (childCount > 0) {
                    remove(wrapper);
                }
            }
        }
        //2.批量删除留言
        removeBatchByIds(ids);
    }

    /**
     * 管理员回复留言
     *
     * @param messageReplyDTO 回复DTO
     * @param request         请求对象
     */
    @Override
    public void adminReply(MessageReplyDTO messageReplyDTO, HttpServletRequest request) {
        //1.拷贝属性
        Messages messages = BeanUtil.copyProperties(messageReplyDTO, Messages.class);
        //2.处理其中的markdown内容
        if (messageReplyDTO.getIsMarkdown() != null && messageReplyDTO.getIsMarkdown() == 1) {
            String html = MarkdownUtil.toHtml(messageReplyDTO.getContent());
            messages.setContentHtml(html);
        } else {
            messages.setContentHtml(messageReplyDTO.getContent());
        }
        //3.设置管理员回复标识
        messages.setIsAdminReply(StatusConstant.ENABLE);
        messages.setIsApproved(StatusConstant.ENABLE);//默认审核通过
        messages.setIsEdited(StatusConstant.DISABLE);
        messages.setCreateTime(LocalDateTime.now());
        messages.setUpdateTime(LocalDateTime.now());

        //4.捕获Ip/地理位置/UserAgent
        if (request != null) {
            String clientIp = IpUtil.getClientIp(request);
            Map<String, String> geoInfo = IpUtil.getGeoInfo(clientIp);
            String province = geoInfo.getOrDefault("province", "");
            String city = geoInfo.getOrDefault("city", "");
            String location = province.isEmpty() && city.isEmpty() ? null
                    : province.equals(city) ? province
                    : String.format("%s-%s", province, city).replaceAll("^-|-$", "");
            if (location != null && !location.isEmpty()) {
                messages.setLocation(location);
            }
            String userAgent = request.getHeader("User-Agent");
            String osName = userAgentService.getOsName(userAgent);
            messages.setUserAgentOs(osName);
            String browserName = userAgentService.getBrowserName(userAgent);
            messages.setUserAgentBrowser(browserName);
        }

        //5.保存到数据库
        save(messages);

        //6.检查父留言是否开启邮箱通知
        if (messageReplyDTO.getParentId() != null) {
            notifyParentIfNeeded(messageReplyDTO.getParentId(), websiteProperties.getTitle(),
                    messageReplyDTO.getContent(), "message");
        }

        log.info("管理员回复留言成功: parentId={}, content={}", messageReplyDTO.getParentId(), messageReplyDTO.getContent());
    }

    /**
     * 获取留言树形结构
     *
     * @param userId 用户id
     * @return 留言树形结构
     */
    @Override
    public List<MessageVO> getMessageTree(Long userId) {
        //1.构建查询条件：已审核通过 或 属于当前用户的留言
        LambdaQueryWrapper<Messages> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(Messages::getIsApproved, StatusConstant.ENABLE)
                .or(w -> {
                    if (userId != null) {
                        w.eq(Messages::getUserId, userId);
                    }
                })
        ).orderByAsc(Messages::getCreateTime);

        //2.查询所有留言
        List<Messages> allMessages = list(queryWrapper);

        //3.转换为 VO 列表
        List<MessageVO> allMessageVOs = allMessages.stream()
                .map(msg -> BeanUtil.copyProperties(msg, MessageVO.class))
                .toList();

        //4.构建树形结构
        List<MessageVO> rootMessages = new ArrayList<>();
        Map<Long, MessageVO> messageMap = allMessageVOs.stream()
                .collect(Collectors.toMap(MessageVO::getId, m -> m));

        for (MessageVO msg : allMessageVOs) {
            if (msg.getRootId() == null || msg.getRootId() == 0) {
                msg.setChildren(new ArrayList<>());
                rootMessages.add(msg);
            } else {
                MessageVO rootMsg = messageMap.get(msg.getRootId());
                if (rootMsg != null) {
                    if (rootMsg.getChildren() == null) {
                        rootMsg.setChildren(new ArrayList<>());
                    }
                    rootMsg.getChildren().add(msg);
                }
            }
        }

        //5.返回根留言列表
        return rootMessages;
    }

    /**
     * 编辑留言
     *
     * @param editDTO 编辑留言 DTO
     * @param userId  当前登录用户 ID（由 Controller 从 SecurityContext 取出）
     */
    @Override
    public void editMessage(MessageEditDTO editDTO, Long userId) {
        //1.查询留言
        Messages messages = getById(editDTO.getId());
        if (messages == null) {
            throw new ValidationException(MessageConstant.MESSAGE_NOT_FOUND);
        }
        if (!messages.getUserId().equals(userId)) {
            throw new ValidationException(MessageConstant.MESSAGE_NOT_EDIT);
        }

        //2.拷贝属性
        Messages updateMessage = new Messages();
        updateMessage.setId(editDTO.getId());
        updateMessage.setContent(editDTO.getContent());

        if (editDTO.getIsMarkdown() != null && editDTO.getIsMarkdown() == 1) {
            updateMessage.setContentHtml(MarkdownUtil.toHtml(editDTO.getContent()));
        } else {
            updateMessage.setContentHtml(editDTO.getContent());
        }

        //3.更新数据库
        updateById(updateMessage);
        log.info("用户编辑留言成功: id={}, userId={}", editDTO.getId(), userId);
    }

    /**
     * 用户删除留言
     * @param id 留言id
     * @param userId 用户id
     */
    @Override
    @Transactional
    public void visitorDeleteMessage(Long id, Long userId) {
        //1.查询留言
        Messages messages = getById(id);
        if (messages == null){
            throw new ValidationException(MessageConstant.MESSAGE_NOT_FOUND);
        }
        if (!messages.getUserId().equals(userId)){
            throw new ValidationException(MessageConstant.MESSAGE_NOT_DELETE);
        }

        //2.如果是根留言，级联删除所有子留言
        if (messages.getRootId() == null || messages.getRootId() == 0) {
            LambdaQueryWrapper<Messages> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Messages::getRootId, id);
            long childCount = count(wrapper);
            if (childCount > 0){
                remove(wrapper);
            }
        }

        //3.删除留言本身
        removeById(id);
        log.info("用户删除留言成功: id={}, userId={}", id, userId);
    }


//<==========私有辅助方法==========>

    /**
     * 检查父留言是否开启邮箱通知，如果是则发送通知邮件
     * <p>
     * 父留言作者邮箱从 sys_user 表查询，按 user_id 关联。
     * </p>
     *
     * @param parentId      父留言ID
     * @param replyNickname 回复者昵称
     * @param replyContent  回复内容
     * @param type          类型
     */
    private void notifyParentIfNeeded(Long parentId, String replyNickname, String replyContent, String type) {
        //1.判空校验
        if (parentId == null) {
            return;
        }
        try {
            //2.查询父留言
            Messages parentMessage = getById(parentId);
            if (parentMessage == null
                    || parentMessage.getIsNotice() == null
                    || parentMessage.getIsNotice() != 1) {
                return;
            }

            //3.父留言作者必须存在 userId（旧匿名数据可能为 null，跳过通知）
            Long parentUserId = parentMessage.getUserId();
            if (parentUserId == null) {
                return;
            }

            //4.通过 sys_user 查询父留言作者的邮箱与昵称
            SysUser parentUser = sysUserMapper.selectById(parentUserId);
            if (parentUser == null
                    || parentUser.getEmail() == null
                    || parentUser.getEmail().isEmpty()) {
                return;
            }

            //5.异步发送回复通知邮件
            asyncEmailService.sendReplyNotificationAsync(
                    parentUser.getEmail(),
                    parentUser.getNickname(),
                    parentMessage.getContent(),
                    replyNickname,
                    replyContent,
                    type
            );

            log.info("留言回复通知已派发: parentId={}, toEmail={}", parentId, parentUser.getEmail());
        } catch (Exception e) {
            log.error("发送留言回复通知邮件异常: parentId={}, ex={}", parentId, e.getMessage());
        }
    }

    /**
     * 统计总留言数
     * @return 总留言数
     */
    @Override
    public Integer countTotal() {
        return Math.toIntExact(count());
    }

    /**
     * 统计待审核留言数
     * @return 待审核留言数
     */
    @Override
    public Integer countPending() {
        LambdaQueryWrapper<Messages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Messages::getIsApproved, StatusConstant.DISABLE);
        return Math.toIntExact(count(wrapper));
    }
}
