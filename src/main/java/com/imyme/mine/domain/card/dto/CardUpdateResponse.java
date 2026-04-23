package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.Card;
import java.time.LocalDateTime;

public record CardUpdateResponse(
    Long id,
    String title,
    LocalDateTime updatedAt
) {
    public static CardUpdateResponse from(Card card) {
        return new CardUpdateResponse(
            card.getId(),
            card.getTitle(),
            card.getUpdatedAt()
        );
    }
}
