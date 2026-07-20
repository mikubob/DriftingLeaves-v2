package com.xuan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置类
 * <p>
 * 注册 Web 层相关组件：拦截器、跨域支持、消息转换器。
 * </p>
 *
 * <h3>跨域配置说明</h3>
 * <p>
 * {@code allowCredentials(true)} 是 Token Cookie 下发机制的必要条件：
 * 浏览器在跨域请求携带 Cookie 时，要求响应头显式声明允许携带凭证，
 * 否则浏览器会拒绝读取响应并丢弃 Set-Cookie 头。
 * </p>
 *
 * @author xuan
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    /**
     * 全局 ObjectMapper，用于自定义 JSON 序列化（如时间格式）
     */
    private final ObjectMapper objectMapper;

    /**
     * 注册自定义拦截器
     * <p>
     * 当前仅保留 API 缓存控制拦截器，认证由 Spring Security Resource Server 统一接管。
     * </p>
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // API 响应禁止 CDN/浏览器缓存，防止 GET 请求返回过期数据
        // 对于管理端、博客端、CV 端、首页接口均生效
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                response.setHeader("Pragma", "no-cache");
                return true;
            }
        }).addPathPatterns("/admin/**", "/blog/**", "/cv/**", "/home/**");
    }

    /**
     * 配置跨域支持
     * <p>
     * 关键配置项：
     * </p>
     * <ul>
     *     <li>{@code allowedOriginPatterns("*")}：允许所有源（生产环境建议指定具体域名）</li>
     *     <li>{@code allowCredentials(true)}：允许携带凭证（Cookie），Token Cookie 模式必需</li>
     *     <li>{@code maxAge(3600)}：预检请求缓存 1 小时，减少 OPTIONS 请求次数</li>
     * </ul>
     *
     * @param registry 跨域注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")  // 允许所有源，或指定域名
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的HTTP方法
                .allowedHeaders("*")
                .allowCredentials(true)  // Cookie 模式必需：允许浏览器跨域携带 Cookie
                .maxAge(3600);  // 预检请求缓存时间
    }

    /**
     * 扩展消息转换器
     * <p>
     * 将 Java 对象转换为 JSON 格式的响应数据。
     * 使用全局配置的 ObjectMapper（已包含自定义时间格式），插入到转换器链的头部以确保优先使用。
     * </p>
     *
     * @param converters 消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 创建消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        // 设置对象转换器，使用全局配置的 ObjectMapper（已包含自定义时间格式）
        converter.setObjectMapper(objectMapper);
        // 将消息转换器加入到容器最前部，确保优先匹配
        converters.add(0, converter);
    }
}
