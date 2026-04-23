package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * 챌린지 히스토리 조회 응답 (GET /challenges)
 */
@Builder
public record ChallengeHistoryResponse(
        @Schema(description = "챌린지 목록")
        List<ChallengeItem> challenges,

        @Schema(description = "페이지네이션 메타")
        Meta meta
) {
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChallengeItem(
            @Schema(description = "챌린지 ID", example = "1")
            Long id,

            @Schema(description = "키워드 이름", example = "자기소개")
            String keywordName,

            @Schema(description = "챌린지 날짜", example = "2026-03-12")
            LocalDate challengeDate,

            @Schema(description = "챌린지 상태", example = "COMPLETED")
            ChallengeStatus status,

            @Schema(description = "참여자 수 (COMPLETED 전에는 0)", example = "15")
            int participantCount,

            @Schema(description = "내 참여 요약 (미참여 또는 미완료면 null)")
            MyParticipation myParticipation
    ) {}

    /**
     * 내 참여 요약 (참여하지 않았거나 미완료면 null)
     */
    @Builder
    public record MyParticipation(
            @Schema(description = "점수", example = "85")
            Integer score,

            @Schema(description = "순위", example = "3")
            Integer rank,

            @Schema(description = "상위 퍼센트", example = "75.0")
            Double percentile
    ) {}

    @Builder
    public record Meta(
            @Schema(description = "현재 페이지 항목 수", example = "20")
            int size,

            @Schema(description = "다음 페이지 존재 여부", example = "true")
            boolean hasNext,

            @Schema(description = "다음 페이지 커서 (마지막 페이지면 null)")
            String nextCursor
    ) {}
}