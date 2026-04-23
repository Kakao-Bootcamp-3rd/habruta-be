package com.imyme.mine.domain.category.dto;

import com.imyme.mine.domain.category.entity.Category;

public record CategoryResponse(
    Long id,
    String name,
    Integer displayOrder,
    Boolean isActive
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getDisplayOrder(),
            category.getIsActive()
        );
    }
}
