package com.imyme.mine.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CardUpdateRequest(
    @NotBlank(message = "제목은 필수입니다.")
    @Size(min = 1, max = 20, message = "제목은 1~20자여야 합니다.")
    String title
) {}
