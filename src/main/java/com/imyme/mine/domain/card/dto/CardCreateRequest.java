package com.imyme.mine.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CardCreateRequest(

    @NotNull(message = "카테고리 ID는 필수입니다.")
    Long categoryId,

    @NotNull(message = "키워드 ID는 필수입니다.")
    Long keywordId,

    @NotBlank(message = "제목은 필수입니다.")
    @Size(min = 1, max = 20, message = "제목은 1~20자여야 합니다.")
    String title
) {}
