package com.imyme.mine.domain.card.event;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 학습 시도 업로드 완료 이벤트
 * - AttemptService와 SoloService 간 결합도 감소를 위한 도메인 이벤트
 * - STT 완료 후 발행되어 Solo 분석을 트리거
 */
@Getter
public class AttemptUploadedEvent {

    private final Long attemptId;
    private final Long userId;
    private final Long cardId;
    private final String userText;
    private final Map<String, Object> criteria;
    private final List<Map<String, Object>> history;

    public AttemptUploadedEvent(
        Long attemptId,
        Long userId,
        Long cardId,
        String userText,
        Map<String, Object> criteria,
        List<Map<String, Object>> history
    ) {
        this.attemptId = attemptId;
        this.userId = userId;
        this.cardId = cardId;
        this.userText = userText;
        this.criteria = criteria;
        this.history = history;
    }
}