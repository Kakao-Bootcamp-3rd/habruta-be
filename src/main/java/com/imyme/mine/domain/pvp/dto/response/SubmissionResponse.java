package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 제출 정보 응답 (4.5, 4.6)
 */
@Getter
@AllArgsConstructor
@Builder
public class SubmissionResponse {
    @Schema(description = "제출 ID", example = "1")
    private Long submissionId;

    @Schema(description = "방 ID", example = "1")
    private Long roomId;

    @Schema(description = "S3 업로드용 Presigned PUT URL")
    private String uploadUrl;

    @Schema(description = "S3 Object Key")
    private String audioUrl;

    @Schema(description = "URL 유효기간 (초)", example = "300")
    private Integer expiresIn;

    @Schema(description = "제출 상태", example = "PENDING")
    private PvpSubmissionStatus status;

    @Schema(description = "제출 시각")
    private LocalDateTime submittedAt;

    @Schema(description = "안내 메시지")
    private String message;
}
