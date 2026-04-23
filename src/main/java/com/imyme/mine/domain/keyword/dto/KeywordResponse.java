package com.imyme.mine.domain.keyword.dto;

import com.imyme.mine.domain.keyword.entity.Keyword;

public record KeywordResponse(
    Long id,
    String name,
    Integer displayOrder,
    Boolean isActive
) {
    public static KeywordResponse from(Keyword keyword) {
        return new KeywordResponse(
            keyword.getId(),
            keyword.getName(),
            keyword.getDisplayOrder(),
            keyword.getIsActive()
        );
    }
}
