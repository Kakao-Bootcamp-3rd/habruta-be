package com.imyme.mine.domain.pvp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/**
 * 녹음 제출 완료 요청 (4.6)
 */
public record CompleteSubmissionRequest(
        @Schema(description = "녹음 길이 (초)", example = "45")
        @Positive(message = "재생 시간은 양수여야 합니다")
        Integer durationSeconds
) {
}
