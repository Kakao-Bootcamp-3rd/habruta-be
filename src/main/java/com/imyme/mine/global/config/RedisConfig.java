package com.imyme.mine.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import com.imyme.mine.domain.learning.messaging.SoloRedisSubscriber;
import com.imyme.mine.domain.pvp.messaging.PvpRedisSubscriber;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 설정
 * - RedisTemplate: 범용 Redis 데이터 저장/조회
 * - RedisMessageListenerContainer: Pub/Sub 리스너 컨테이너
 * - CacheManager: Spring Cache 추상화 (애플리케이션 캐싱)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * RedisTemplate 빈 등록
     * - Key: String
     * - Value: JSON (Jackson2JsonRedisSerializer)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ObjectMapper 설정 (LocalDateTime 등 Java 8 시간 타입 지원)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Jackson2JsonRedisSerializer 설정
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        // Key는 String, Value는 JSON으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis Pub/Sub 리스너 컨테이너
     * - 메시지를 수신할 리스너를 등록하고 관리
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PvpRedisSubscriber pvpRedisSubscriber,
            SoloRedisSubscriber soloRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(pvpRedisSubscriber, new PatternTopic("pvp:room:*"));
        container.addMessageListener(soloRedisSubscriber, new PatternTopic("solo:result:*"));
        return container;
    }

    /**
     * Spring Cache Manager 설정
     * - 캐시별 TTL 개별 설정
     * - Jackson JSON 직렬화 (redis-cli 디버깅 용이)
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Jackson ObjectMapper 설정 (Record 지원)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Record는 final 클래스이므로 NON_FINAL이 아닌 EVERYTHING을 사용해야 타입 정보(@class)가 포함됨
        // 타입 정보가 없으면 역직렬화 시 LinkedHashMap으로 조립 → ClassCastException → 500
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.EVERYTHING,
            JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // 기본 캐시 설정 (TTL 10분)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            .disableCachingNullValues();  // null 값은 캐싱하지 않음

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Tier 1: 마스터 데이터 (2시간)
        cacheConfigs.put("categories",
            defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigs.put("keywords",
            defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigs.put("keywords:criteria",
            defaultConfig.entryTtl(Duration.ofHours(24)));  // AI 채점 기준 — Knowledge 배치 주기(24h)에 맞춤

        // Tier 2: 동적 데이터
        cacheConfigs.put("userProfile",
            defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("ai:feedback:solo",
            defaultConfig.entryTtl(Duration.ofDays(7)));  // Immutable 데이터
        cacheConfigs.put("ai:feedback:pvp",
            defaultConfig.entryTtl(Duration.ofDays(7)));  // Immutable 데이터

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}