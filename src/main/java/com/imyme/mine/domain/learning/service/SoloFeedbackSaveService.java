package com.imyme.mine.domain.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.ai.dto.solo.SoloFeedback;
import com.imyme.mine.domain.ai.dto.solo.SoloResult;
import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.entity.CardFeedback;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.domain.card.repository.CardFeedbackRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SoloFeedbackSaveService {

    private static final String SOLO_MODEL_VERSION = "gemini-3-pro-preview";

    private final CardAttemptRepository attemptRepository;
    private final CardFeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;
    private final NotificationCreatorService notificationCreatorService;

    @Transactional
    public void save(Long attemptId, SoloResult result) {
        if (result == null) {
            log.warn("Solo feedback result is null - attemptId: {}", attemptId);
            throw new BusinessException(ErrorCode.FEEDBACK_SAVE_FAILED);
        }
        if (result.feedback() == null) {
            log.warn("Solo feedback is null - attemptId: {}", attemptId);
            throw new BusinessException(ErrorCode.FEEDBACK_SAVE_FAILED);
        }

        if (feedbackRepository.existsById(attemptId)) {
            log.info("Solo feedback already saved - attemptId: {}", attemptId);
            return;
        }

        // CardAttempt 조회 (Card 및 User와 함께 fetch join)
        // Virtual Thread에서 실행되므로 lazy loading 방지 필수
        CardAttempt attempt = attemptRepository.findByIdWithCardAndUser(attemptId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTEMPT_NOT_FOUND));

        String feedbackJson = convertSoloFeedbackToJson(result.feedback());

        int score = result.overallScore() != null ? result.overallScore() : 0;
        short level = (short) calculateLevel(score);

        // @MapsId를 사용하므로 attemptId만 설정 (attempt는 JPA가 관리)
        CardFeedback newFeedback = CardFeedback.builder()
            .attempt(attempt)
            .overallScore(score)
            .level(level)
            .feedbackJson(feedbackJson)
            .modelVersion(SOLO_MODEL_VERSION)
            .build();

        feedbackRepository.save(newFeedback);

        // 피드백 저장 완료 시 상태를 COMPLETED로 전환 + 카드 통계 갱신
        Card card = attempt.getCard();
        boolean wasGhost = card.getAttemptCount() == null || card.getAttemptCount() == 0;
        attempt.complete(); // sttText는 이미 설정되어 있음
        card.completeAttempt(level);
        if (wasGhost) {
            card.getUser().incrementActiveCardCount();
        }

        log.info("Solo feedback saved - attemptId: {}, score: {}, level: {}",
            attemptId, result.overallScore(), result.level());

        // 채점 완료 알림 (트랜잭션 커밋 후 FCM 비동기 발송)
        notificationCreatorService.create(
            card.getUser().getId(),
            NotificationType.SOLO_RESULT,
            "학습 결과가 도착했어요!",
            "'" + card.getTitle() + "' 카드의 피드백을 확인해보세요.",
            card.getId(),   // path: /mypage/cards/{cardId}
            "CARD_ATTEMPT"
        );
    }

    /**
     * score → level 변환
     * 0→0 / 1~20→1 / 21~40→2 / 41~60→3 / 61~80→4 / 81~100→5
     */
    private static int calculateLevel(int score) {
        if (score <= 0)  return 0;
        if (score <= 20) return 1;
        if (score <= 40) return 2;
        if (score <= 60) return 3;
        if (score <= 80) return 4;
        return 5;
    }

    private String convertSoloFeedbackToJson(SoloFeedback feedback) {
        try {
            Map<String, Object> feedbackMap = new HashMap<>();
            feedbackMap.put("summary", feedback.summarize());
            String keywords = (feedback.keyword() == null || feedback.keyword().isEmpty())
                ? ""
                : String.join(", ", feedback.keyword());
            feedbackMap.put("keywords", keywords);
            feedbackMap.put("facts", feedback.facts());
            feedbackMap.put("understanding", feedback.understanding());
            feedbackMap.put("socratic_feedback", feedback.personalized());

            return objectMapper.writeValueAsString(feedbackMap);

        } catch (Exception e) {
            log.error("Solo feedback JSON conversion failed", e);
            throw new BusinessException(ErrorCode.FEEDBACK_SAVE_FAILED);
        }
    }
}
