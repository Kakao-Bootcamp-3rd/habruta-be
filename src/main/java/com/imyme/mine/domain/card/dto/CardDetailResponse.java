package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.Card;
import com.imyme.mine.domain.card.entity.CardAttempt;

import java.time.LocalDateTime;
import java.util.List;

public record CardDetailResponse(
    Long id,
    Long categoryId,
    String categoryName,
    Long keywordId,
    String keywordName,
    String title,
    Short bestLevel,
    Short attemptCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<AttemptResponse> attempts
) {
    public static CardDetailResponse of(Card card, List<CardAttempt> attempts) {
        List<AttemptResponse> attemptResponses = attempts.stream()
            .map(AttemptResponse::from)
            .toList();

        return new CardDetailResponse(
            card.getId(),
            card.getCategory().getId(),
            card.getCategory().getName(),
            card.getKeyword().getId(),
            card.getKeyword().getName(),
            card.getTitle(),
            card.getBestLevel(),
            card.getAttemptCount(),
            card.getCreatedAt(),
            card.getUpdatedAt(),
            attemptResponses
        );
    }
}
