package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.Card;
import java.time.LocalDateTime;

public record CardListItem(
    Long id,
    Long categoryId,
    String categoryName,
    Long keywordId,
    String keywordName,
    String title,
    Short bestLevel,
    Short attemptCount,
    LocalDateTime createdAt
) {
    /**
     * Card 엔티티 → CardListItem 변환
     *
     * [ JOIN FETCH 전제 ]
     * - Repository에서 category, keyword를 JOIN FETCH로 조회
     * - N+1 문제 없이 안전하게 접근 가능
     */
    public static CardListItem from(Card card) {
        return new CardListItem(
            card.getId(),
            card.getCategory().getId(),
            card.getCategory().getName(),
            card.getKeyword().getId(),
            card.getKeyword().getName(),
            card.getTitle(),
            card.getBestLevel(),
            card.getAttemptCount(),
            card.getCreatedAt()
        );
    }
}
