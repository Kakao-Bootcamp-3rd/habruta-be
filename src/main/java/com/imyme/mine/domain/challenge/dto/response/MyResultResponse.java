package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 내 챌린지 결과 조회 응답 (GET /challenges/{challengeId}/my-result)
 * - challenge != COMPLETED: myResult=null, message/expectedCompletionAt 포함
 * - challenge == COMPLETED: myResult 포함
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyResultResponse(
        @Schema(description = "챌린지 ID", example = "1")
        Long challengeId,

        @Schema(description = "키워드 이름", example = "자기소개")
        String keywordName,

        @Schema(description = "챌린지 날짜", example = "2026-03-12")
        LocalDate challengeDate,

        @Schema(description = "챌린지 상태", example = "COMPLETED")
        ChallengeStatus status,

        @Schema(description = "안내 메시지 (미완료 시)", example = "AI 분석 중입니다. 잠시만 기다려주세요.")
        String message,

        @Schema(description = "예상 완료 시각 (미완료 시, 추정치)")
        LocalDateTime expectedCompletionAt,

        @Schema(description = "내 결과 (COMPLETED일 때만 존재)")
        MyResult myResult
) {
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyResult(
            @Schema(description = "참여 ID", example = "1")
            Long attemptId,

            @Schema(description = "점수", example = "85")
            Integer score,

            @Schema(description = "순위", example = "3")
            Integer rank,

            @Schema(description = "상위 퍼센트", example = "75.0")
            Double percentile,

            @Schema(description = "녹음 길이 (초)", example = "45")
            Integer durationSeconds,

            @Schema(description = "STT 변환 텍스트")
            String sttText,

            @Schema(description = "AI 피드백 (JSON 객체)")
            Object feedback
    ) {}
}