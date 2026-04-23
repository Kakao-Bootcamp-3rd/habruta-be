package com.imyme.mine.domain.keyword.dto;

import com.imyme.mine.domain.category.entity.Category;

import java.util.List;

public record CategoryKeywordsResponse(
    CategoryInfo category,
    List<KeywordResponse> keywords
) {
    public CategoryKeywordsResponse {
        keywords = List.copyOf(keywords);
    }
    public record CategoryInfo(
        Long id,
        String name
    ) {
        public static CategoryInfo from(Category category) {
            return new CategoryInfo(category.getId(), category.getName());
        }
    }

    public static CategoryKeywordsResponse of(Category category, List<KeywordResponse> keywords) {
        return new CategoryKeywordsResponse(CategoryInfo.from(category), keywords);
    }
}