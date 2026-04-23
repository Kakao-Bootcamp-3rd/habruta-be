package com.imyme.mine.domain.card.dto;

import com.imyme.mine.domain.card.entity.Card;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CardListResponse(
    List<CardListItem> cards,
    Pagination pagination
) {

    public record Pagination(
        @Schema(description = "다음 페이지 커서 (마지막 페이지면 null)")
        String nextCursor,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "페이지 크기", example = "20")
        int limit
    ) {}

    public static CardListResponse of(List<Card> cards, int limit) {

        boolean hasNext = cards.size() > limit;

        List<Card> resultCards = hasNext ? cards.subList(0, limit) : cards;

        List<CardListItem> items = resultCards.stream()
            .map(CardListItem::from)
            .toList();

        String nextCursor = null;
        if (hasNext && !resultCards.isEmpty()) {
            Card lastCard = resultCards.get(resultCards.size() - 1);
            nextCursor = encodeCursor(lastCard);
        }

        return new CardListResponse(
            items,
            new Pagination(nextCursor, hasNext, limit)
        );
    }

    private static String encodeCursor(Card card) {
        String rawCursor = card.getCreatedAt().toString() + "_" + card.getId();
        return java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rawCursor.getBytes());
    }
}
