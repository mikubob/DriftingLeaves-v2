package com.xuan.util;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 角色编码 → 默认昵称映射
 * <p>
 * 用户注册/自动创建时，根据当前被分配的角色填充默认昵称。
 * 若用户同时拥有多个角色，按角色优先级取最高一级对应的昵称。
 * </p>
 */
public final class RoleNicknameMapper {

    private RoleNicknameMapper() {
    }

    /**
     * 角色优先级（数值越小优先级越高）
     */
    private static final Map<String, Integer> ROLE_PRIORITY = Map.of(
            "ADMIN", 1,
            "AUTHOR", 2,
            "AUDITOR", 3,
            "GUEST", 4
    );

    /**
     * 角色编码 → 默认昵称
     */
    private static final Map<String, String> ROLE_NICKNAME = Map.of(
            "ADMIN", "管理员",
            "AUTHOR", "作者",
            "AUDITOR", "审计员",
            "GUEST", "游客"
    );

    /**
     * 根据角色编码列表获取默认昵称
     *
     * @param roleCodes 角色编码列表（不能为空）
     * @return 优先级最高的角色对应的默认昵称；无法识别时返回“游客”
     */
    public static String getNickname(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return ROLE_NICKNAME.get("GUEST");
        }
        return roleCodes.stream()
                .filter(ROLE_PRIORITY::containsKey)
                .min(Comparator.comparingInt(ROLE_PRIORITY::get))
                .map(ROLE_NICKNAME::get)
                .orElse(ROLE_NICKNAME.get("GUEST"));
    }
}
