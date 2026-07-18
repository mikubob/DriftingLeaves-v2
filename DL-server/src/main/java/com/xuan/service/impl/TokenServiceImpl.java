package com.xuan.service.impl;

import com.xuan.constant.JwtClaimsConstant;
import com.xuan.constant.RedisConstant;
import com.xuan.exception.TokenException;
import com.xuan.properties.JwtProperties;
import com.xuan.service.TokenService;
import com.xuan.utils.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Token 服务实现类
 * 核心逻辑：JWT + Redis Set 实现多端登录与令牌管理
 * 优化点：使用 Lua 脚本保证 Token 数量限制的原子性，防止并发绕过限制
 *
 * @author Xuan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    /**
     * 单用户最大 Token 数量限制
     * 防止恶意用户通过大量登录耗尽 Redis 内存
     */
    private static final int MAX_TOKEN_COUNT = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    /**
     * Lua 脚本：原子性地检查数量、添加 Token 并设置过期时间
     * 脚本内容见 resources/lua/token_add.lua
     */
    private DefaultRedisScript<Long> tokenAddScript;

    /**
     * 初始化 Lua 脚本
     */
    @PostConstruct
    public void init() {
        tokenAddScript = new DefaultRedisScript<>();
        tokenAddScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/token_add.lua")));
        tokenAddScript.setResultType(Long.class);
    }

    /**
     * 创建 JWT 令牌并存储到 Redis
     *
     * @param userId 用户 ID
     * @param role   用户角色
     * @return 生成的 JWT 令牌字符串
     */
    @Override
    public String createAndStoreToken(Long userId, Integer role) {
        // 1. 构建 JWT 的 Claims (载荷)
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.ADMIN_ID, userId);
        claims.put(JwtClaimsConstant.ADMIN_ROLE, role);

        // 2. 生成 JWT 字符串
        String token = JwtUtil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getTtl(), // 单位：毫秒
                claims
        );

        String tokenKey = RedisConstant.TOKEN_PREFIX + userId;
        long ttlMillis = jwtProperties.getTtl();

        // 3. 执行 Lua 脚本 (原子操作：检查数量 + 添加 + 设置过期)
        // 这解决了之前代码中 check 和 add 分离导致的并发竞态问题
        try {
            Long result = stringRedisTemplate.execute(
                    tokenAddScript,
                    Collections.singletonList(tokenKey),
                    token,
                    String.valueOf(ttlMillis),
                    String.valueOf(MAX_TOKEN_COUNT)
            );

            if (result != null && result == 1) {
                log.info("用户 [{}] 生成新 Token 成功 (当前总数 <= {})", userId, MAX_TOKEN_COUNT);
                return token;
            } else {
                // 数量超限
                log.warn("用户 [{}] 创建 Token 失败：设备数量已达上限 ({})", userId, MAX_TOKEN_COUNT);
                throw new TokenException("登录设备数量已达上限 (" + MAX_TOKEN_COUNT + ")，请先登出其他设备");
            }
        } catch (TokenException e) {
            // 业务异常直接抛出，保留给前端展示的友好提示
            throw e;
        } catch (Exception e) {
            log.error("用户 [{}] Token 存入 Redis 失败", userId, e);
            throw new TokenException("登录服务暂时不可用，请稍后重试", e);
        }
    }

    /**
     * 验证 Token 有效性
     *
     * @param userId 用户 ID
     * @param token  待验证的 Token
     * @return true: 有效; false: 无效
     */
    @Override
    public boolean isValidToken(Long userId, String token) {
        if (userId == null || token == null || token.isEmpty()) {
            return false;
        }

        String tokenKey = RedisConstant.TOKEN_PREFIX + userId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(tokenKey, token);
        
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 单端登出
     */
    @Override
    public void logout(Long userId, String token) {
        if (userId == null || token == null) {
            return;
        }
        String tokenKey = RedisConstant.TOKEN_PREFIX + userId;
        Long removedCount = stringRedisTemplate.opsForSet().remove(tokenKey, token);
        
        if (removedCount != null && removedCount > 0) {
            log.info("用户 [{}] 单端登出成功", userId);
        }
    }

    /**
     * 全端登出
     */
    @Override
    public void logoutAll(Long userId) {
        if (userId == null) {
            return;
        }
        String tokenKey = RedisConstant.TOKEN_PREFIX + userId;
        Boolean deleted = stringRedisTemplate.delete(tokenKey);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.info("用户 [{}] 全端登出成功", userId);
        }
    }
}
