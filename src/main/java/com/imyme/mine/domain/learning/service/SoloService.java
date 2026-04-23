package com.imyme.mine.domain.learning.service;

import com.imyme.mine.domain.ai.client.AiServerClient;
import com.imyme.mine.domain.ai.dto.solo.*;
import com.imyme.mine.domain.card.event.AttemptUploadedEvent;
import com.imyme.mine.domain.card.service.AttemptSttService;
import com.imyme.mine.domain.learning.messaging.SoloRedisMessage;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Solo 모드 심층 분석 서비스
 * - AI 서버에 분석 요청 및 결과 폴링
 * - Virtual Thread를 사용한 비동기 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoloService {

    private static final String SOLO_RESULT_CHANNEL_PREFIX = "solo:result:";

    private final AiServerClient aiServerClient;
    private final SoloFeedbackSaveService feedbackSaveService;
    private final AttemptSttService attemptSttService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_RETRIES = 60;  // 최대 60회 (3분)
    private static final long POLL_INTERVAL_MS = 3000;  // 3초 간격

    /**
     * 학습 시도 업로드 완료 이벤트 리스너
     * - Event 기반으로 AttemptService와 결합도 감소
     * - @Async로 비동기 처리 (별도 스레드에서 실행)
     */
    @EventListener
    @Async
    public void handleAttemptUploaded(AttemptUploadedEvent event) {
        log.info("AttemptUploadedEvent 수신 - attemptId: {}", event.getAttemptId());

        startSoloAnalysisAsync(
            event.getAttemptId(),
            event.getUserText(),
            event.getCriteria(),
            event.getHistory()
        );
    }

    /**
     * Solo 분석을 비동기로 시작 (HTTP 경로)
     * - AI 서버에 분석 요청 후 Virtual Thread로 백그라운드 폴링
     * - MQ 모드에서는 AttemptUploadedEvent 자체가 발행되지 않으므로 호출되지 않음
     */
    public void startSoloAnalysisAsync(
        Long attemptId,
        String userText,
        Map<String, Object> criteria,
        List<Map<String, Object>> history
    ) {
        log.info("Solo 분석 시작 - attemptId: {}", attemptId);

        try {
            SoloSubmissionRequest request = new SoloSubmissionRequest(
                attemptId, userText, criteria, history
            );
            SoloSubmissionData submission = aiServerClient.submitSolo(request);
            log.info("Solo 분석 요청 완료 - attemptId: {}, status: {}",
                attemptId, submission.status());

            Thread.startVirtualThread(() -> {
                pollAndSaveSoloResult(attemptId);
            });
            log.info("Solo 분석 백그라운드 폴링 시작 - attemptId: {}", attemptId);

        } catch (Exception e) {
            log.error("Solo 분석 요청 실패 - attemptId: {}", attemptId, e);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
    }

    /**
     * 백그라운드에서 결과 폴링 및 저장
     * - 3초마다 AI 서버에 결과 조회
     * - 완료되면 DB에 저장
     * - 최대 3분(60회) 대기
     */
    private void pollAndSaveSoloResult(Long attemptId) {
        log.debug("Solo 폴링 시작 - attemptId: {}", attemptId);

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                // AI 서버에 결과 조회
                SoloResultData response = aiServerClient.pollSoloResult(attemptId);

                log.debug("Solo 폴링 응답 - attemptId: {}, status: {}, retry: {}/{}",
                    attemptId, response.status(), i + 1, MAX_RETRIES);

                // 완료 상태 확인
                if ("completed".equalsIgnoreCase(response.status())) {
                    // 완료되면 DB에 저장
                    if (response.result() != null) {
                        feedbackSaveService.save(attemptId, response.result());
                        redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                            SoloRedisMessage.emit(attemptId, "COMPLETED"));
                        log.info("Solo 분석 완료 및 저장 성공 - attemptId: {}", attemptId);
                    } else {
                        log.warn("Solo 분석 완료되었으나 result가 null - attemptId: {}", attemptId);
                        redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                            SoloRedisMessage.emit(attemptId, "FAILED"));
                    }
                    return;
                }

                // 실패 상태 확인
                if ("failed".equalsIgnoreCase(response.status())) {
                    log.error("Solo 분석 실패 - attemptId: {}", attemptId);
                    attemptSttService.recordFailure(attemptId, "AI_FEEDBACK_FAILED");
                    redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                        SoloRedisMessage.emit(attemptId, "FAILED"));
                    return;
                }

                // 아직 진행 중이면 3초 대기
                Thread.sleep(POLL_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Solo 폴링 중단 - attemptId: {}", attemptId, e);
                attemptSttService.recordFailure(attemptId, "POLLING_INTERRUPTED");
                redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                    SoloRedisMessage.emit(attemptId, "FAILED"));
                throw new RuntimeException("Solo polling interrupted", e);

            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.AI_ANALYSIS_FAILED || e.getErrorCode() == ErrorCode.AI_POLLING_TIMEOUT) {
                    attemptSttService.recordFailure(attemptId, "AI_FEEDBACK_FAILED");
                    redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
                        SoloRedisMessage.emit(attemptId, "FAILED"));
                    return;
                }
                log.error("Solo 폴링 비즈니스 에러 - attemptId: {}, retry: {}", attemptId, i + 1, e);

            } catch (Exception e) {
                log.error("Solo 폴링 에러 - attemptId: {}, retry: {}", attemptId, i + 1, e);
                // 에러 발생 시 계속 재시도
            }
        }

        // 최대 재시도 횟수 초과
        log.warn("Solo 폴링 타임아웃 (3분 초과) - attemptId: {}", attemptId);
        attemptSttService.recordFailure(attemptId, "AI_FEEDBACK_FAILED");
        redisTemplate.convertAndSend(SOLO_RESULT_CHANNEL_PREFIX + attemptId,
            SoloRedisMessage.emit(attemptId, "FAILED"));
    }

}
