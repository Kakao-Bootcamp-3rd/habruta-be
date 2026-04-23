package com.imyme.mine.domain.pvp.messaging;

import com.imyme.mine.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 메시지 발행 서비스
 * - 메인 서버 → AI 서버로 메시지 발행
 * - STT Request, Feedback Request 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * STT Request 발행 (Phase 1)
     * 메인 서버 → AI 서버 (음성 인식 요청)
     *
     * @param payload 발행할 메시지 (DTO)
     */
    public void publishSttRequest(Object payload) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PVP_DIRECT_EXCHANGE,
                    RabbitMQConfig.PVP_STT_REQUEST_ROUTING_KEY,
                    payload
            );
            log.info("[RabbitMQ] STT Request 발행 성공: {}", payload);
        } catch (Exception e) {
            log.error("[RabbitMQ] STT Request 발행 실패", e);
            throw new RuntimeException("STT Request 발행 실패", e);
        }
    }

    /**
     * STT Response 발행 (Phase 1)
     * AI 서버 → 메인 서버 (음성 인식 결과)
     *
     * @param payload 발행할 메시지 (DTO)
     */
    public void publishSttResponse(Object payload) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PVP_DIRECT_EXCHANGE,
                    RabbitMQConfig.PVP_STT_RESPONSE_ROUTING_KEY,
                    payload
            );
            log.info("[RabbitMQ] STT Response 발행 성공: {}", payload);
        } catch (Exception e) {
            log.error("[RabbitMQ] STT Response 발행 실패", e);
            throw new RuntimeException("STT Response 발행 실패", e);
        }
    }

    /**
     * Feedback Request 발행 (Phase 2)
     * 메인 서버 → AI 서버 (피드백 생성 요청)
     *
     * @param payload 발행할 메시지 (DTO)
     */
    public void publishFeedbackRequest(Object payload) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PVP_DIRECT_EXCHANGE,
                    RabbitMQConfig.PVP_FEEDBACK_REQUEST_ROUTING_KEY,
                    payload
            );
            log.info("[RabbitMQ] Feedback Request 발행 성공: {}", payload);
        } catch (Exception e) {
            log.error("[RabbitMQ] Feedback Request 발행 실패", e);
            throw new RuntimeException("Feedback Request 발행 실패", e);
        }
    }

    /**
     * Feedback Response 발행 (Phase 2)
     * AI 서버 → 메인 서버 (피드백 생성 결과)
     *
     * @param payload 발행할 메시지 (DTO)
     */
    public void publishFeedbackResponse(Object payload) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PVP_DIRECT_EXCHANGE,
                    RabbitMQConfig.PVP_FEEDBACK_RESPONSE_ROUTING_KEY,
                    payload
            );
            log.info("[RabbitMQ] Feedback Response 발행 성공: {}", payload);
        } catch (Exception e) {
            log.error("[RabbitMQ] Feedback Response 발행 실패", e);
            throw new RuntimeException("Feedback Response 발행 실패", e);
        }
    }
}
