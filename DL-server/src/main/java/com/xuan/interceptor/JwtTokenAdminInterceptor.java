package com.xuan.interceptor;

import cn.hutool.core.util.StrUtil;
import com.xuan.constant.JwtClaimsConstant;
import com.xuan.constant.AdminRoleConstant;
import com.xuan.constant.MessageConstant;
import com.xuan.context.BaseContext;
import com.xuan.exception.GuestReadOnlyException;
import com.xuan.exception.NotLoginException;
import com.xuan.exception.UnauthorizedException;
import com.xuan.properties.JwtProperties;
import com.xuan.service.TokenService;
import com.xuan.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;
    private final TokenService tokenService;

    /**
     * 校验jwt
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @return 拦截结果
     * @throws Exception 抛出的异常
     */
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        // 从 Cookie 中获取令牌
        String token = getTokenFromCookie(request);

        // 如果令牌为空，抛出未登录异常
        if(StrUtil.isBlank(token)){
            throw new NotLoginException(MessageConstant.NOT_LOGIN);
        }

        // 校验令牌
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getSecretKey(), token);
            Long adminId = Long.valueOf(claims.get(JwtClaimsConstant.ADMIN_ID).toString());
            Integer role = Integer.valueOf(claims.get(JwtClaimsConstant.ADMIN_ROLE).toString());
            log.info("jwt校验,当前管理员id：{}, role: {}", adminId, role);

            // 检测令牌是否在服务端存在
            if(!tokenService.isValidToken(adminId, token)){
                throw new UnauthorizedException(MessageConstant.NOT_AUTHORIZED);
            }

            // 游客账号(role=0)只允许GET查询操作，禁止增删改
            if(role.equals(AdminRoleConstant.VISITOR) && !"GET".equalsIgnoreCase(request.getMethod())){
                throw new GuestReadOnlyException(MessageConstant.GUEST_READ_ONLY);
            }

            BaseContext.setCurrentId(adminId);
            BaseContext.setCurrentRole(role);
            // 通过，放行
            return true;
        }catch (GuestReadOnlyException ex){
            throw ex;
        }
        catch (Exception ex) {
            // 校验失败，抛出未授权异常
            throw new UnauthorizedException(MessageConstant.NOT_AUTHORIZED);
        }
    }

    /**
     * 从 Cookie 中读取 Token（自动 URL 解码）
     * @param request
     * @return Token 字符串，不存在则返回 null
     */
    private String getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (jwtProperties.getCookieName().equals(cookie.getName())) {
                return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * 后置处理 - 清理ThreadLocal
     * @param request
     * @param response
     * @param handler
     * @param ex
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            // 清理ThreadLocal，防止虚拟线程复用导致adminId串用
            BaseContext.removeCurrentId();
            BaseContext.removeCurrentRole();
        } catch (Exception e) {
            log.error("清理ThreadLocal失败", e);
        }
    }
}
