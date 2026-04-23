package com.imyme.mine.domain.challenge.dto.response;

import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 챌린지 참여 시작 응답 (POST /challenges/{challengeId}/attempts)
 * - 신규 생성: HTTP 201
 * - PENDING 재사용: HTTP 200
 */
@Builder
public record CreateAttemptResponse(
        @Schema(description = "참여 ID", example = "1")
        Long attemptId,

        @Schema(description = "챌린지 ID", example = "1")
        Long challengeId,

        @Schema(description = "S3 업로드용 Presigned PUT URL")
        String uploadUrl,

        @Schema(description = "S3 Object Key (upload-complete 요청 시 전달)", example = "challenges/1/2/3_uuid")
        String objectKey,

        @Schema(description = "URL 유효기간 (초)", example = "300")
        int expiresIn,

        @Schema(description = "참여 상태", example = "PENDING")
        ChallengeAttemptStatus status
) {}