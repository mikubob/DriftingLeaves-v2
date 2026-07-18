package com.xuan.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xuan.constant.StatusConstant;
import com.xuan.dto.DailyViewCountDTO;
import com.xuan.dto.ProvinceCountDTO;
import com.xuan.dto.VisitorPageQueryDTO;
import com.xuan.dto.VisitorRecordDTO;
import com.xuan.entity.Visitors;
import com.xuan.mapper.VisitorMapper;
import com.xuan.result.PageResult;
import com.xuan.service.AsyncVisitorService;
import com.xuan.service.BlockService;
import com.xuan.service.FingerprintService;
import com.xuan.service.IVisitorService;
import com.xuan.utils.IpUtil;
import com.xuan.vo.VisitorQueryVO;
import com.xuan.vo.VisitorRecordVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.xuan.constant.RedisConstant.BLOCKED_KEY;
import static com.xuan.constant.RedisConstant.VISITOR_KEY;

/**
 * 访客服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorServiceImpl extends ServiceImpl<VisitorMapper, Visitors> implements IVisitorService {

    private final RedisTemplate<String,Object> redisTemplate;
    private final FingerprintService fingerprintService;
    private final BlockService blockService;
    private final AsyncVisitorService asyncVisitorService;

    /**
     * 记录访客访问信息
     * @param visitorRecordDTO 访客记录信息
     * @param httpRequest 请求对象
     * @return 访问记录信息
     */
    @Override
    public VisitorRecordVO recordVisitorViewInfo(VisitorRecordDTO visitorRecordDTO, HttpServletRequest httpRequest) {
        // 生成/获取会话Id
        String sessionId = getOrCreateSessionId(httpRequest);

        // 生成设备指纹
        String fingerprint = fingerprintService.generateVisitorFingerprint(visitorRecordDTO,httpRequest);

        //获取ip
        String ip = IpUtil.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        //检查访客是否在缓存中有封禁记录
        blockService.checkIfBlocked(fingerprint);

        //检查请求频率
        blockService.checkRateLimit(fingerprint,ip);

        //查找或创建访客记录
        Visitors visitor = findOrCreateVisitor(fingerprint,sessionId,userAgent,ip);

        // 异步处理：IP地理位置查询 + 访客地理信息更新 + 浏览记录写入
        // 传递 visitorId 而非对象引用，避免主线程与异步线程共享可变对象导致竞态条件
        asyncVisitorService.processGeoAndRecordViewAsync(
                visitor.getId(), ip, userAgent,
                visitorRecordDTO.getPagePath(),
                visitorRecordDTO.getReferer(),
                visitorRecordDTO.getPageTitle()
        );

        // 封装VO（立即返回，不等待异步操作完成）
        return VisitorRecordVO.builder()
                .visitorFingerprint(fingerprint)
                .sessionId(sessionId)
                .visitorId(visitor.getId())
                .isNewVisitor(visitor.getTotalViews() <= 1)
                .build();
    }

    /**
     * 分页查询访客列表
     * @param visitorPageQueryDTO 查询参数
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(VisitorPageQueryDTO visitorPageQueryDTO) {
        //1.创建分页对象
        Page<Visitors> page=new Page<>(visitorPageQueryDTO.getPage(), visitorPageQueryDTO.getPageSize());
        //2.创建查询条件
        Page<Visitors> vistorsPage = page(page, buildQueryWrapper(visitorPageQueryDTO));
        //3.转换为 QueryVO 并返回
        Page<VisitorQueryVO> voPage = new Page<>(vistorsPage.getCurrent(), vistorsPage.getSize(), vistorsPage.getTotal());
        voPage.setRecords(vistorsPage.getRecords().stream()
                .map(v -> cn.hutool.core.bean.BeanUtil.copyProperties(v, VisitorQueryVO.class))
                .toList());
        return PageResult.fromIPage(voPage);
    }

    /**
     * 批量封禁访客
     * <p>
     * 将指定访客标记为封禁状态，封禁时长为1天。
     * 封禁后访客将无法访问网站，直到封禁期满或被手动解封。
     * </p>
     *
     * @param ids 访客ID列表，不能为空
     */
    @Override
    public void batchBlock(List<Long> ids) {
        // 获取当前时间，用于设置封禁过期时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        // 批量更新访客状态：设置封禁状态为启用、封禁过期时间为1天后、更新修改时间
        lambdaUpdate()
                .set(Visitors::getIsBlocked, StatusConstant.ENABLE) // 设置封禁状态
                .set(Visitors::getExpiresAt, now.plusDays(1)) // 封禁时长为1天
                .set(Visitors::getUpdateTime, now) // 更新修改时间
                .in(Visitors::getId, ids) // 匹配指定ID列表
                .update();
    }

    /**
     * 批量解封访客
     * <p>
     * 将指定访客从封禁状态恢复为正常状态。
     * 解封操作会：
     * <ul>
     *     <li>清除Redis中的封禁缓存标记（BLOCKED_KEY）</li>
     *     <li>清除访客信息缓存（VISITOR_KEY），避免缓存不一致</li>
     *     <li>更新数据库中的封禁状态为未封禁，并清空封禁过期时间</li>
     * </ul>
     * </p>
     *
     * @param ids 访客ID列表，不能为空
     */
    @Override
    public void batchUnblock(List<Long> ids) {
        // 遍历每个访客ID，清除Redis中的封禁相关缓存
        for (Long id : ids) {
            Visitors visitor = getById(id);
            if (visitor != null && visitor.getFingerprint() != null) {
                // 构建封禁缓存Key并删除
                String blockedKey = BLOCKED_KEY + visitor.getFingerprint();
                redisTemplate.delete(blockedKey);
                // 清除访客信息缓存，避免缓存不一致
                redisTemplate.delete(VISITOR_KEY + visitor.getFingerprint());
            }
        }
        LocalDateTime now = LocalDateTime.now();
        // 批量更新访客状态：解除封禁状态、清空过期时间
        lambdaUpdate()
                .set(Visitors::getIsBlocked, StatusConstant.DISABLE) // 设置为未封禁状态
                .set(Visitors::getUpdateTime, now) // 更新修改时间
                .set(Visitors::getExpiresAt, null) // 清空封禁过期时间
                .in(Visitors::getId, ids) // 匹配指定ID列表
                .update();
    }

    /**
     * 根据设备指纹查询访客信息
     * @param fingerprint 设备指纹
     * @return 访客信息，不存在则返回null
     */
    @Override
    public Visitors findVisitorByFingerprint(String fingerprint) {
        return lambdaQuery()
                .eq(Visitors::getFingerprint, fingerprint)
                .one();
    }

    /**
     * 统计访客总数
     * @return 访客总数
     */
    @Override
    public Integer countTotal() {
        return Math.toIntExact(count());
    }

    /**
     * 统计今日新增访客数
     * @return 今日新增访客数
     */
    @Override
    public Integer countToday() {
        return baseMapper.countToday();
    }

    /**
     * 获取每日新增访客数
     * @param begin 起始时间
     * @param end 结束时间
     * @return 每日新增访客数
     */
    @Override
    public List<DailyViewCountDTO> getDailyNewVisitorStats(LocalDate begin, LocalDate end) {
        return baseMapper.getDailyNewVisitorStats(begin, end);
    }

    /**
     * 获取省份分布
     * @return 省份分布
     */
    @Override
    public List<ProvinceCountDTO> getProvinceDistribution() {
        return baseMapper.getProvinceDistribution();
    }

    /**
     * 批量删除访客
     * <p>
     * 删除指定访客记录，同时清除Redis中的相关缓存。
     * 删除操作会：
     * <ul>
     *     <li>清除Redis中的封禁缓存标记（BLOCKED_KEY）</li>
     *     <li>清除访客信息缓存（VISITOR_KEY）</li>
     *     <li>从数据库中删除访客记录</li>
     * </ul>
     * </p>
     *
     * @param ids 访客ID列表，不能为空
     */
    @Override
    public void batchDeleteVisitors(List<Long> ids) {
        for (Long id : ids) {
            Visitors visitor = getById(id);
            if (visitor != null && visitor.getFingerprint() != null) {
                String blockedKey = BLOCKED_KEY + visitor.getFingerprint();
                redisTemplate.delete(blockedKey);
                redisTemplate.delete(VISITOR_KEY + visitor.getFingerprint());
            }
        }
        removeByIds(ids);
    }

    /**
     * 获取或创建会话ID
     * <p>
     * 如果当前请求已有关联的会话，则返回现有会话ID；
     * 否则创建新会话并记录访问时间。
     * </p>
     * @param request HTTP请求对象
     * @return 会话ID
     */
    private String getOrCreateSessionId(HttpServletRequest request) {
        // 尝试获取已存在的会话（不创建新会话）
        HttpSession session = request.getSession(false);
        if (session == null) {
            // 会话不存在，创建新会话
            session = request.getSession(true);
            // 记录会话创建时间，用于后续分析
            session.setAttribute("visitTime", LocalDateTime.now());
        }
        return session.getId();
    }

    /**
     * 查找或创建访客记录（不含地理位置，地理位置由异步服务填充）
     * @param fingerprint 设备指纹
     * @param sessionId 会话ID
     * @param userAgent 用户代理
     * @param ip IP地址
     * @return 访客记录
     */
    private Visitors findOrCreateVisitor(String fingerprint, String sessionId,
                                         String userAgent, String ip){
        // 尝试从Redis中获取访客信息
        String cacheKey = VISITOR_KEY + fingerprint;
        Visitors visitor = (Visitors) redisTemplate.opsForValue().get(cacheKey);

        if(visitor!=null){
            // 缓存命中,更新基本信息
            log.info("【访客追踪】缓存命中: id={}, fingerprint={}, ip={}, cachedViews={}",
                    visitor.getId(), fingerprint, ip, visitor.getTotalViews());
            visitor.setSessionId(sessionId);
            visitor.setIp(ip);
            visitor.setLastVisitTime(LocalDateTime.now());
            visitor.setTotalViews(visitor.getTotalViews() + 1);
            updateById(visitor);
            // 回写Redis缓存，保持缓存数据与数据库同步（修复totalViews等字段不一致的问题）
            redisTemplate.opsForValue().set(cacheKey, visitor, 1, TimeUnit.HOURS);
            return visitor;
        }

        // 缓存未命中，通过指纹查找访客
        visitor = findVisitorByFingerprint(fingerprint);

        if(visitor==null){
            // 新访客：创建记录（地理位置字段由异步任务填充）
            log.info("【访客追踪】新访客创建: fingerprint={}, ip={}", fingerprint, ip);
            visitor = Visitors.builder()
                    .fingerprint(fingerprint)
                    .sessionId(sessionId)
                    .ip(ip)
                    .userAgent(userAgent)
                    .firstVisitTime(LocalDateTime.now())
                    .lastVisitTime(LocalDateTime.now())
                    .totalViews(1L)
                    .isBlocked(StatusConstant.DISABLE)
                    .build();
            try {
                save(visitor);
                log.info("【访客追踪】新访客插入成功: id={}, fingerprint={}", visitor.getId(), fingerprint);
            } catch (DuplicateKeyException e) {
                // 并发场景：另一个请求已经插入了相同指纹的访客，回退到数据库查询
                log.warn("【访客追踪】并发创建，回退查询: fingerprint={}", fingerprint);
                visitor = findVisitorByFingerprint(fingerprint);
                if (visitor != null) {
                    visitor.setLastVisitTime(LocalDateTime.now());
                    visitor.setTotalViews(visitor.getTotalViews() + 1);
                    visitor.setSessionId(sessionId);
                    visitor.setIp(ip);
                    updateById(visitor);
                }
            }
        }else{
            // 老访客：更新基本信息
            log.info("【访客追踪】老访客更新: id={}, fingerprint={}, ip={}, dbViews={}",
                    visitor.getId(), fingerprint, ip, visitor.getTotalViews());
            visitor.setLastVisitTime(LocalDateTime.now());
            visitor.setTotalViews(visitor.getTotalViews() + 1);

            // 如果session已过期或不同，则视为新的浏览器会话
            boolean sessionExpired = !sessionId.equals(visitor.getSessionId());
            if(sessionExpired){
                visitor.setSessionId(sessionId);
            }

            visitor.setIp(ip);
            updateById(visitor);
        }

        // 统一写入/更新Redis缓存
        if (visitor != null) {
            redisTemplate.opsForValue().set(cacheKey, visitor, 1, TimeUnit.HOURS);
        }
        return visitor;
    }

    /**
     * 构建访客查询条件
     * <p>
     * 根据查询参数动态构建查询条件，支持按国家、省份、城市进行模糊查询，
     * 按封禁状态进行精确查询，结果按最后访问时间降序排列。
     * </p>
     * @param visitorPageQueryDTO 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<Visitors> buildQueryWrapper(VisitorPageQueryDTO visitorPageQueryDTO) {
        LambdaQueryWrapper<Visitors> queryWrapper = new LambdaQueryWrapper<>();
        // 国家条件：模糊匹配
        if (StrUtil.isNotBlank(visitorPageQueryDTO.getCountry())) {
            queryWrapper.like(Visitors::getCountry, visitorPageQueryDTO.getCountry());
        }
        // 省份条件：模糊匹配
        if (StrUtil.isNotBlank(visitorPageQueryDTO.getProvince())) {
            queryWrapper.like(Visitors::getProvince, visitorPageQueryDTO.getProvince());
        }
        // 城市条件：模糊匹配
        if (StrUtil.isNotBlank(visitorPageQueryDTO.getCity())) {
            queryWrapper.like(Visitors::getCity, visitorPageQueryDTO.getCity());
        }
        // 封禁状态条件：精确匹配
        if (visitorPageQueryDTO.getStatus() != null) {
            queryWrapper.eq(Visitors::getIsBlocked, visitorPageQueryDTO.getStatus());
        }
        // 按最后访问时间降序排列，最近的访问记录排在前面
        queryWrapper.orderByDesc(Visitors::getLastVisitTime);
        return queryWrapper;
    }
}
