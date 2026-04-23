package com.imyme.mine.domain.learning.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.learning.messaging.dto.SoloFeedbackResponseDto;
import com.imyme.mine.domain.learning.messaging.dto.SoloSttResponseDto;
import com.imyme.mine.domain.learning.service.SoloMqConsumerService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Solo MQ 메시지 수신 (Consumer)
 * - AI 서버로부터 STT Response, Feedback Response 수신
 * - DTO 파싱 후 SoloMqConsumerService에 비즈니스 로직 위임
 * - Manual Ack: 처리 성공 시 basicAck, 실패 시 basicNack (DLQ로 이동 없음, discard)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoloMqConsumer {

    private final ObjectMapper objectMapper;
    private final SoloMqConsumerService soloMqConsumerService;

    /**
     * STT 응답 수신 (AI → Main)
     * - 음성→텍스트 변환 결과 수신
     * - SUCCESS: STT 저장 + Feedback Request 발행
     * - FAIL: 시도 실패 처리
     */
    @RabbitListener(queues = "${solo.mq.queue.stt-response}")
    public void consumeSttResponse(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            SoloSttResponseDto dto = objectMapper.readValue(message.getBody(), SoloSttResponseDto.class);
            log.info("[Solo MQ] STT Response 수신 - attemptId: {}, status: {}", dto.attemptId(), dto.status());

            soloMqConsumerService.handleSttResponse(dto);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[Solo MQ] STT Response 처리 실패", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * Feedback 응답 수신 (AI → Main)
     * - AI 분석 결과(점수, 피드백) 수신
     * - SUCCESS: 피드백 저장 + 시도 COMPLETED
     * - FAIL: 시도 실패 처리
     */
    @RabbitListener(queues = "${solo.mq.queue.feedback-response}")
    public void consumeFeedbackResponse(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            SoloFeedbackResponseDto dto = objectMapper.readValue(message.getBody(), SoloFeedbackResponseDto.class);
            log.info("[Solo MQ] Feedback Response 수신 - attemptId: {}, status: {}", dto.attemptId(), dto.status());

            soloMqConsumerService.handleFeedbackResponse(dto);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[Solo MQ] Feedback Response 처리 실패", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}