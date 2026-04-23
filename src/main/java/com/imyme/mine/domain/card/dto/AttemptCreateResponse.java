package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.CardAttempt;

import java.time.LocalDateTime;

public record AttemptCreateResponse(

    Long cardId,
    Long attemptId,
    Short attemptNo,
    String status,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    String message

) {

    public static AttemptCreateResponse of(CardAttempt attempt, LocalDateTime expiresAt) {
        return new AttemptCreateResponse(
            attempt.getCard().getId(),
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getCreatedAt(),
            expiresAt,
            "시도가 생성되었습니다. 10분 내에 업로드를 완료해주세요."
        );
    }
}