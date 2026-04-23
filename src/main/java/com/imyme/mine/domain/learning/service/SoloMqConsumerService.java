package com.imyme.mine.domain.learning.service;

import com.imyme.mine.domain.ai.dto.solo.SoloFeedback;
import com.imyme.mine.domain.ai.dto.solo.SoloResult;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.service.AttemptSttService;
import com.imyme.mine.domain.knowledge.service.KnowledgeBaseService;
import com.imyme.mine.domain.learning.messaging.SoloMqPublisher;
import com.imyme.mine.domain.learning.messaging.SoloRedisMessage;
import com.imyme.mine.domain.learning.messaging.dto.SoloFeedbackRequestDto;
import com.imyme.mine.domain.learning.messaging.dto.SoloFeedbackResponseDto;
import com.imyme.mine.domain.learning.messaging.dto.SoloSttResponseDto;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Solo MQ Consumer 비즈니스 로직
 * - STT Response: STT 결과 저장 + Feedback Request 발행
 * - Feedback Response: 피드백 저장 or 실패 처리
 * - Redis SETNX로 request_id 기반 중복 처리 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloMqConsumerService {

    private static final String REQUEST_ID_KEY_PREFIX = "solo:mq:request:";
    private static final Duration REQUEST_ID_TTL = Duration.ofDays(1);

    private static final String SOLO_RESULT_CHANNEL_PREFIX = "solo:result:";

    private final AttemptSttService attemptSttService;
    private final SoloFeedbackSaveService feedbackSaveService;
    private final SoloMqPublisher soloMqPublisher;
    private final CardAttemptRepository cardAttemptRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * STT Response 처리 (AI → Main)
     * - request_id 중복 방지 (Redis SETNX)
     * - SUCCESS: STT 텍스트 저장 + Feedback Request 발행
     * - FAIL: 시도 실패 상태 저장
     */
    @Transactional
    public void handleSttResponse(SoloSttResponseDto dto) {
        Long attemptId = dto.attemptId();
        log.info("[Solo MQ] STT Response 처리 - attemptId: {}, status: {}", attemptId, dto.status());

        if (!acquireRequestLock(dto.requestId())) {
            log.warn("[Solo MQ] STT Response 중복 수신 무시 - requestId: {}, attemptId: {}", dto.requestId(), attemptId);
            return;
        }

        if ("FAIL".equalsIgnoreCase(dto.status())) {
            log.warn("[Solo MQ] STT 실패 - attemptId: {}, error: {}", attemptId, dto.error());
            attemptSttService.recordSttFailure(attemptId, "STT_RECOGNIZE_FAILED");
            return;
        }

        attemptSttService.recordSttSuccess(attemptId, dto.sttText());

        // TX 커밋 후 MQ 발행 - 커밋 전 발행 시 TX 롤백되면 고아 메시지 발생
        SoloFeedbackRequestDto feedbackRequest = buildFeedbackRequestDto(attemptId, dto.userId(), dto.sttText());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    soloMqPublisher.publishFeedbackRequest(feedbackRequest);
                    log.info("[Solo MQ] Feedback Request 발행 완료 - attemptId: {}", attemptId);
                } catch (Exception e) {
                    log.error("[Solo MQ] Feedback Request 발행 실패 - attemptId: {}", attemptId, e);
                    attemptSttService.recordFailure(attemptId, "AI_FEEDBACK_FAILED");
                    redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                        SoloRedisMessage.emit(attemptId, "FAILED"));
                }
            }
        });
    }

    /**
     * Feedback Response 처리 (AI → Main)
     * - request_id 중복 방지 (Redis SETNX)
     * - SUCCESS: 피드백 저장 (SoloFeedbackSaveService 위임)
     * - FAIL: 시도 실패 상태 저장
     */
    public void handleFeedbackResponse(SoloFeedbackResponseDto dto) {
        Long attemptId = dto.attemptId();
        log.info("[Solo MQ] Feedback Response 처리 - attemptId: {}, status: {}", attemptId, dto.status());

        if (!acquireRequestLock(dto.requestId())) {
            log.warn("[Solo MQ] Feedback Response 중복 수신 무시 - requestId: {}, attemptId: {}", dto.requestId(), attemptId);
            return;
        }

        if ("FAIL".equalsIgnoreCase(dto.status())) {
            log.warn("[Solo MQ] Feedback 실패 - attemptId: {}, error: {}", attemptId, dto.error());
            attemptSttService.recordFailure(attemptId, "AI_FEEDBACK_FAILED");
            redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                SoloRedisMessage.emit(attemptId, "FAILED"));
            return;
        }

        SoloFeedbackResponseDto.FeedbackDto fb = dto.feedback();
        if (fb == null) {
            log.warn("[Solo MQ] Feedback 데이터 없음 - attemptId: {}", attemptId);
            attemptSttService.recordFailure(attemptId, "AI_FEEDBACK_FAILED");
            redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                SoloRedisMessage.emit(attemptId, "FAILED"));
            return;
        }

        SoloFeedback soloFeedback = new SoloFeedback(
            fb.summary(), fb.keywords(), fb.facts(), fb.understanding(), fb.personalizedFeedback()
        );
        SoloResult soloResult = new SoloResult(fb.score(), null, soloFeedback);
        feedbackSaveService.save(attemptId, soloResult);  // @Transactional → 여기서 커밋
        redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
            SoloRedisMessage.emit(attemptId, "COMPLETED"));  // 커밋 후 SSE 발송
    }

    /**
     * STT 성공 후 Feedback Request DTO 생성 (TX 내 Lazy Loading)
     * - Card/Keyword Lazy Loading: @Transactional 컨텍스트 내에서 실행됨
     * - model_answer: List<String> → "\n\n" 구분 String으로 결합
     * - MQ 발행은 afterCommit에서 수행 (TX 롤백 시 고아 메시지 방지)
     */
    private SoloFeedbackRequestDto buildFeedbackRequestDto(Long attemptId, Long userId, String sttText) {
        CardAttempt attempt = cardAttemptRepository.findByIdWithCardAndUser(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        Card card = attempt.getCard();
        String keywordName = card.getKeyword().getName();
        List<String> modelAnswers = knowledgeBaseService.getModelAnswersByKeyword(card.getKeyword().getId());
        String modelAnswer = modelAnswers.isEmpty() ? null : String.join("\n\n", modelAnswers);

        return SoloFeedbackRequestDto.builder()
            .requestId(UUID.randomUUID().toString())
            .attemptId(attemptId)
            .userId(userId)
            .sttText(sttText)
            .criteria(SoloFeedbackRequestDto.CriteriaDto.builder()
                .keyword(keywordName)
                .modelAnswer(modelAnswer)
                .build())
            .history(List.of())
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * Redis SETNX로 request_id 중복 처리 방지
     * - 최초 수신: true (처리 진행)
     * - 중복 수신: false (처리 스킵)
     */
    private boolean acquireRequestLock(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return true; // request_id 없으면 중복 체크 스킵
        }
        String key = REQUEST_ID_KEY_PREFIX + requestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", REQUEST_ID_TTL);
        return Boolean.TRUE.equals(acquired);
    }
}