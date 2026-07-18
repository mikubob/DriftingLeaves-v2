package com.xuan.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置
 * <p>
 * 作用：
 * 1. 统一 Java 8 时间类型（LocalDateTime/LocalDate/LocalTime）的序列化和反序列化格式
 * 2. 使 HTTP 响应中的时间格式与 application.yaml 中的 date-format 保持一致
 * 3. 解决 spring.jackson.date-format 只对 java.util.Date 生效、不影响 LocalDateTime 的问题
 * <p>
 * 格式约定：
 * - LocalDateTime → yyyy-MM-dd HH:mm:ss
 * - LocalDate → yyyy-MM-dd
 * - LocalTime → HH:mm:ss
 *
 */
@Configuration
public class JacksonConfig {

    /** 日期时间格式 */
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm";
    /** 日期格式 */
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 时间格式 */
    private static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 自定义 Jackson ObjectMapper 构建器
     * <p>
     * 通过 Jackson2ObjectMapperBuilderCustomizer 注入自定义的 JavaTimeModule，
     * Spring Boot 会自动将其应用到全局 ObjectMapper 中（包括 Spring MVC 的 HTTP 序列化）。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);

            // 序列化器（Java 对象 → JSON 字符串）
            builder.serializers(
                    new LocalDateTimeSerializer(dateTimeFormatter),
                    new LocalDateSerializer(dateFormatter),
                    new LocalTimeSerializer(timeFormatter));

            // 反序列化器（JSON 字符串 → Java 对象）
            builder.deserializers(
                    new LocalDateTimeDeserializer(dateTimeFormatter),
                    new LocalDateDeserializer(dateFormatter),
                    new LocalTimeDeserializer(timeFormatter));

            // 禁用未知字段
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }
}