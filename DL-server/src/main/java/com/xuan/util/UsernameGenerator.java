package com.xuan.util;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

/**
 * 随机用户名生成器
 * <p>
 * 生成规则：user_ + 时间戳后 6 位 + UUID 前 8 位，形如 {@code user_123456abcdef1234}。
 * </p>
 */
@Component
public class UsernameGenerator {

    /**
     * 生成随机用户名
     *
     * @return 随机用户名
     */
    public String generate() {
        String ts = String.valueOf(System.currentTimeMillis());
        String timestampSuffix = ts.substring(Math.max(0, ts.length() - 6));
        String uuidSuffix = IdUtil.simpleUUID().substring(0, 8);
        return "user_" + timestampSuffix + uuidSuffix;
    }
}
