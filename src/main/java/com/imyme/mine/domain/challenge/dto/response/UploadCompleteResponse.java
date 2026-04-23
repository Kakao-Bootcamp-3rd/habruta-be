package com.imyme.mine.domain.challenge.dto.response;

import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 업로드 완료 확정 응답 (POST /challenges/{challengeId}/attempts/{attemptId}/upload-complete)
 */
@Builder
public record UploadCompleteResponse(
        @Schema(description = "참여 ID", example = "1")
        Long attemptId,

        @Schema(description = "참여 상태", example = "UPLOADED")
        ChallengeAttemptStatus status,

        @Schema(description = "제출 시각")
        LocalDateTime submittedAt,

        @Schema(description = "안내 메시지", example = "제출이 완료되었습니다. 결과 집계를 기다려주세요.")
        String message
) {}