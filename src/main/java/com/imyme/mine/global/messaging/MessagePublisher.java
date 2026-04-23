package com.imyme.mine.global.messaging;

/**
 * 메시지 발행 인터페이스
 * - 결합도를 낮추기 위한 추상화 레이어
 * - Redis, RabbitMQ, Kafka 등 다양한 메시지 브로커로 교체 가능
 */
public interface MessagePublisher {

    /**
     * 특정 채널(토픽)에 메시지 발행
     *
     * @param channel 채널명 (예: "pvp:room:123")
     * @param message 발행할 메시지 객체
     */
    void publish(String channel, Object message);
}