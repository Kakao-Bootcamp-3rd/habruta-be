package com.imyme.mine.domain.challenge.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.challenge.dto.message.ChallengeFeedbackResponseDto;
import com.imyme.mine.domain.challenge.service.ChallengeAsyncService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 챌린지 MQ 메시지 수신 리스너
 *
 * <p>AI 서버 → Spring: AI 채점 완료 메시지 수신.
 * Manual Ack 모드: 처리 성공 시에만 Ack, 실패 시 Nack → DLQ 이동.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeMqConsumer {

    private final ChallengeAsyncService challengeAsyncService;
    private final ObjectMapper objectMapper;

    /**
     * 챌린지 AI 채점 결과 수신
     *
     * @param channel  RabbitMQ 채널 (Ack/Nack용)
     * @param message  RabbitMQ 원본 메시지
     */
    @RabbitListener(queues = "#{challengeMqProperties.queue.feedbackResponse}")
    public void consumeFeedbackResponse(Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[Challenge MQ] 채점 결과 수신: deliveryTag={}", deliveryTag);

            ChallengeFeedbackResponseDto dto =
                    objectMapper.readValue(message.getBody(), ChallengeFeedbackResponseDto.class);

            challengeAsyncService.handleFeedbackResponse(dto);

            channel.basicAck(deliveryTag, false);
            log.info("[Challenge MQ] 처리 완료 (Ack): deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("[Challenge MQ] 처리 실패: deliveryTag={}", deliveryTag, e);
            try {
                channel.basicNack(deliveryTag, false, false); // DLQ로 이동
                log.warn("[Challenge MQ] Nack → DLQ: deliveryTag={}", deliveryTag);
            } catch (IOException ioException) {
                log.error("[Challenge MQ] Nack 실패", ioException);
            }
        }
    }
}