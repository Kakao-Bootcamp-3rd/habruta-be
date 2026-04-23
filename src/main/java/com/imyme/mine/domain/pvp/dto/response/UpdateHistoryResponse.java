package com.imyme.mine.domain.pvp.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 기록 숨기기 응답 (4.9)
 */
@Getter
@AllArgsConstructor
@Builder
public class UpdateHistoryResponse {
    @Schema(description = "기록 ID", example = "1")
    private Long historyId;

    @Schema(description = "숨김 여부", example = "true")
    private Boolean isHidden;

    @Schema(description = "변경 시각")
    private LocalDateTime updatedAt;
}