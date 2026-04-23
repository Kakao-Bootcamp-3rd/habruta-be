package com.imyme.mine.domain.pvp.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.pvp.dto.message.FeedbackResponseDto;
import com.imyme.mine.domain.pvp.dto.message.SttResponseDto;
import com.imyme.mine.domain.pvp.service.PvpMqConsumerService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RabbitMQ 메시지 수신 리스너
 * - AI 서버 → 메인 서버로 메시지 수신
 * - STT Response, Feedback Response 수신
 * - Manual Ack: 메시지 처리 성공 시에만 Ack (신뢰성 보장)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQMessageConsumer {

    private final PvpMqConsumerService pvpMqConsumerService;
    private final ObjectMapper objectMapper;

    /**
     * STT Response 수신 (Phase 1)
     * AI 서버 → 메인 서버 (음성 인식 결과)
     *
     * @param payload 수신한 메시지 (DTO)
     * @param channel RabbitMQ 채널
     * @param message RabbitMQ 메시지 (Ack/Nack용)
     */
    @RabbitListener(queues = "pvp.stt.response")
    public void consumeSttResponse(Object payload, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[RabbitMQ] STT Response 수신: {}", payload);

            SttResponseDto dto = objectMapper.readValue(message.getBody(), SttResponseDto.class);
            pvpMqConsumerService.handleSttResponse(dto);

            channel.basicAck(deliveryTag, false);
            log.info("[RabbitMQ] STT Response 처리 완료 (Ack): deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("[RabbitMQ] STT Response 처리 실패", e);
            try {
                channel.basicNack(deliveryTag, false, false); // DLQ로 이동
                log.warn("[RabbitMQ] STT Response Nack (DLQ): deliveryTag={}", deliveryTag);
            } catch (IOException ioException) {
                log.error("[RabbitMQ] Nack 실패", ioException);
            }
        }
    }

    /**
     * Feedback Response 수신 (Phase 2)
     * AI 서버 → 메인 서버 (피드백 생성 결과)
     *
     * @param payload 수신한 메시지 (DTO)
     * @param channel RabbitMQ 채널
     * @param message RabbitMQ 메시지 (Ack/Nack용)
     */
    @RabbitListener(queues = "pvp.feedback.response")
    public void consumeFeedbackResponse(Object payload, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[RabbitMQ] Feedback Response 수신: {}", payload);

            FeedbackResponseDto dto = objectMapper.readValue(message.getBody(), FeedbackResponseDto.class);
            pvpMqConsumerService.handleFeedbackResponse(dto);

            channel.basicAck(deliveryTag, false);
            log.info("[RabbitMQ] Feedback Response 처리 완료 (Ack): deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("[RabbitMQ] Feedback Response 처리 실패", e);
            try {
                channel.basicNack(deliveryTag, false, false); // DLQ로 이동
                log.warn("[RabbitMQ] Feedback Response Nack (DLQ): deliveryTag={}", deliveryTag);
            } catch (IOException ioException) {
                log.error("[RabbitMQ] Nack 실패", ioException);
            }
        }
    }
}