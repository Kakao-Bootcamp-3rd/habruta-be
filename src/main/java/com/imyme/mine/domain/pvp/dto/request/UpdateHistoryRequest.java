package com.imyme.mine.domain.pvp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 기록 숨기기 요청 (4.9)
 */
public record UpdateHistoryRequest(
        @Schema(description = "숨김 여부", example = "true")
        @NotNull(message = "숨김 여부를 입력해주세요")
        Boolean isHidden
) {
}
