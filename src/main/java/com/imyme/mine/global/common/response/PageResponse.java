package com.imyme.mine.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 응답 DTO
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(List<T> content, PageMeta meta) {

    // Spring Data Page 객체를 PageResponse로 변환
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .meta(PageMeta.from(page))
            .build();
    }

    // 페이징 메타데이터
    @Getter
    @Builder
    public static class PageMeta {
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final boolean isFirst;
        private final boolean isLast;

        public static PageMeta from(Page<?> page) {
            return PageMeta.builder()
                .page(page.getNumber() + 1)  // 0-based → 1-based
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
        }
    }
}
