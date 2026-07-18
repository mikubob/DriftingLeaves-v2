package com.xuan.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class RedisConfiguration {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 创建自定义的 ObjectMapper
     * 核心功能：
     * 1. 支持 Java 8 时间类型自定义格式
     * 2. 开启多态类型支持 (解决集合反序列化问题)
     * 3. 忽略未知属性
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. 配置可见性：允许序列化所有字段（包括私有）
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 2. 配置反序列化特性
        // 忽略 JSON 中存在但 Java 类中没有的字段，防止报错
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 防止空 Bean 序列化报错
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 禁用将日期写为时间戳，改为字符串
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. 配置类型安全 (防止反序列化漏洞)
        // 使用 LaissezFaireSubTypeValidator 允许所有子类。
        // 注意：如果安全性要求极高，建议替换为自定义白名单 Validator
        PolymorphicTypeValidator validator = LaissezFaireSubTypeValidator.instance;
        objectMapper.activateDefaultTyping(
                validator,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        // 4. 注册 JavaTimeModule 并自定义时间格式
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);

        // --- 序列化器 (Java -> JSON) ---
        javaTimeModule.addSerializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(dateTimeFormatter));
        javaTimeModule.addSerializer(LocalDate.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(dateFormatter));
        javaTimeModule.addSerializer(LocalTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer(timeFormatter));

        // --- 反序列化器 (JSON -> Java) ---
        javaTimeModule.addDeserializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(dateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDate.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(dateFormatter));
        javaTimeModule.addDeserializer(LocalTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer(timeFormatter));

        objectMapper.registerModule(javaTimeModule);

        return objectMapper;
    }

    /**
     * 创建自定义的 Jackson2JsonRedisSerializer，解决集合类型序列化问题
     */
    @Bean
    public GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer() {
        ObjectMapper objectMapper = createObjectMapper();
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    /**
     * 配置 RedisTemplate
     * 这是手动操作 Redis 的核心入口
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                       GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // --- Key 的序列化 ---
        // 使用 StringRedisSerializer，保证 key 是人类可读的字符串，不会出现乱码
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);

        // --- Value 的序列化 ---
        // 使用自定义的 GenericJackson2JsonRedisSerializer
        // 优势：自动处理泛型、集合、多态对象，且支持自定义时间格式
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);

        // 初始化
        redisTemplate.afterPropertiesSet();

        log.info("RedisTemplate initialized successfully with custom JSON serializer and time formatting.");
        return redisTemplate;
    }

    /**
     * 配置Spring Cache使用Redis作为缓存后端
     * 不同缓存空间使用不同的TTL策略
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                     GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer) {

        // 默认缓存配置：30分钟过期，使用自定义的序列化器
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer))
                .disableCachingNullValues();

        // 不同缓存空间的TTL策略
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 静态数据：很少变化，缓存1小时
        cacheConfigurations.put("personalInfo", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("socialMedia", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("skills", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("experiences", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("friendLinks", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 文章相关：适中变化频率，缓存30分钟
        cacheConfigurations.put("articleCategories", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("articleTags", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("articleList", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("articleDetail", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("articleArchive", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("hotArticles", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 统计数据：变化频繁，短时间缓存
        cacheConfigurations.put("blogReport", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 音乐列表：很少变化，缓存1小时
        cacheConfigurations.put("musicList", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 系统配置：极少变化，缓存1小时
        cacheConfigurations.put("systemConfig", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Sitemap/RSS Feed：缓存30分钟
        cacheConfigurations.put("sitemap", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("rssFeed", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
