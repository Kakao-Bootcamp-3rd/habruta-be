package com.imyme.mine.domain.learning.messaging;

import com.imyme.mine.domain.learning.messaging.dto.SoloFeedbackRequestDto;
import com.imyme.mine.domain.learning.messaging.dto.SoloSttRequestDto;
import com.imyme.mine.global.config.SoloMqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Solo MQ 메시지 발행 (Producer)
 * - STT Request: 오디오 URL → AI 서버 전달
 * - Feedback Request: STT 텍스트 + 평가 기준 → AI 서버 전달
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloMqPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final SoloMqProperties soloMqProperties;

    /**
     * STT 요청 발행 (Main → AI)
     * - 오디오 URL을 AI 서버에 전달하여 음성→텍스트 변환 요청
     */
    public void publishSttRequest(SoloSttRequestDto payload) {
        log.info("[Solo MQ] STT Request 발행 - attemptId: {}", payload.attemptId());
        rabbitTemplate.convertAndSend(
            soloMqProperties.getExchange(),
            soloMqProperties.getRouting().getSttRequest(),
            payload
        );
    }

    /**
     * Feedback 요청 발행 (Main → AI)
     * - STT 텍스트 + 평가 기준을 AI 서버에 전달하여 피드백 분석 요청
     */
    public void publishFeedbackRequest(SoloFeedbackRequestDto payload) {
        log.info("[Solo MQ] Feedback Request 발행 - attemptId: {}", payload.attemptId());
        rabbitTemplate.convertAndSend(
            soloMqProperties.getExchange(),
            soloMqProperties.getRouting().getFeedbackRequest(),
            payload
        );
    }
}
