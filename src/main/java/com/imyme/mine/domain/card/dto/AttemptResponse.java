package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.CardAttempt;
import java.time.LocalDateTime;

public record AttemptResponse(
    Long id,
    Short attemptNo,
    String status,
    Integer durationSeconds,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
    public static AttemptResponse from(CardAttempt attempt) {
        return new AttemptResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getStatus().name(),
            attempt.getDurationSeconds(),
            attempt.getCreatedAt(),
            attempt.getFinishedAt()
        );
    }
}
