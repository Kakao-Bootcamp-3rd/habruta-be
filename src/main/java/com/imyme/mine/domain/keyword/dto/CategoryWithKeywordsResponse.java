package com.imyme.mine.domain.keyword.dto;

import java.util.List;

public record CategoryWithKeywordsResponse(
    Long categoryId,
    String categoryName,
    List<KeywordSimpleResponse> keywords
) {
    public CategoryWithKeywordsResponse {
        keywords = List.copyOf(keywords);
    }

    public record KeywordSimpleResponse(
        Long id,
        String name
    ) {}
}
