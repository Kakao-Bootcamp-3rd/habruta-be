package com.imyme.mine.domain.challenge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 업로드 완료 확정 요청 (POST /challenges/{challengeId}/attempts/{attemptId}/upload-complete)
 */
public record CompleteUploadRequest(
        @Schema(description = "S3 Object Key (참여 시작 응답에서 받은 값)", example = "challenges/1/2/3_uuid")
        @NotBlank String objectKey,

        @Schema(description = "녹음 길이 (초)", example = "45")
        @NotNull @Min(1) Integer durationSeconds
) {}