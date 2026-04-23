package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.Card;
import java.time.LocalDateTime;

public record CardResponse(
    Long id,
    Long categoryId,
    String categoryName,
    Long keywordId,
    String keywordName,
    String title,
    Short bestLevel,
    Short attemptCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CardResponse from(Card card) {
        return new CardResponse(
            card.getId(),
            card.getCategory().getId(),
            card.getCategory().getName(),
            card.getKeyword().getId(),
            card.getKeyword().getName(),
            card.getTitle(),
            card.getBestLevel(),
            card.getAttemptCount(),
            card.getCreatedAt(),
            card.getUpdatedAt()
        );
    }
}
