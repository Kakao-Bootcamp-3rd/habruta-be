package com.imyme.mine.global.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 메시지 발행자
 * - MessagePublisher 인터페이스 구현
 * - RedisTemplate을 사용하여 Pub/Sub 채널에 메시지 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessagePublisher implements MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis Pub/Sub 채널에 메시지 발행
     *
     * @param channel 채널명 (예: "pvp:room:123")
     * @param message 발행할 메시지 객체 (JSON으로 직렬화됨)
     */
    @Override
    public void publish(String channel, Object message) {
        try {
            log.debug("Publishing message to channel: {}, message: {}", channel, message);
            redisTemplate.convertAndSend(channel, message);
            log.info("Message published successfully to channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to publish message to channel: {}", channel, e);
            throw new RuntimeException("메시지 발행 실패", e);
        }
    }
}