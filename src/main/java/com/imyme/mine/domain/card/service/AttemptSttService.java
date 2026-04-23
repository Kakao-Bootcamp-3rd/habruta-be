package com.imyme.mine.domain.card.service;

import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.learning.messaging.SoloRedisMessage;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptSttService {

    private static final String SOLO_RESULT_CHANNEL_PREFIX = "solo:result:";

    private final CardAttemptRepository cardAttemptRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void recordSttSuccess(Long attemptId, String sttText) {
        CardAttempt attempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
        attempt.recordSttResult(sttText);
        log.info("STT 처리 성공 - attemptId: {}, 텍스트 길이: {}", attemptId, sttText.length());
        // STT 완료 → FEEDBACK_GENERATION 단계로 전환 알림 (연결 유지)
        redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
            SoloRedisMessage.push(attemptId, "PROCESSING", "FEEDBACK_GENERATION"));
    }

    @Transactional
    public void recordSttFailure(Long attemptId, String errorCode) {
        CardAttempt attempt = cardAttemptRepository.findById(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));
        attempt.fail(errorCode);
        log.info("STT 처리 실패 상태 저장 - attemptId: {}, errorCode: {}", attemptId, errorCode);
        // STT 실패 → 클라이언트에 FAILED 알림 (연결 종료)
        redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
            SoloRedisMessage.emit(attemptId, "FAILED"));
    }

    /**
     * 분석 실패 상태만 DB에 저장 (SSE emit 없음)
     * SoloService Virtual Thread 내부에서 호출 — 별도 Spring Bean을 통해야 @Transactional이 적용됨
     */
    @Transactional
    public void recordFailure(Long attemptId, String errorCode) {
        cardAttemptRepository.findById(attemptId).ifPresent(attempt -> {
            attempt.fail(errorCode);
            log.info("분석 실패 상태 저장 - attemptId: {}, errorCode: {}", attemptId, errorCode);
        });
    }
}
