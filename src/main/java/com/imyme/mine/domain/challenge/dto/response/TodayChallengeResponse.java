package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 오늘의 챌린지 조회 응답 (GET /challenges/today)
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodayChallengeResponse(
        @Schema(description = "챌린지 ID", example = "1")
        Long id,

        @Schema(description = "키워드 정보")
        KeywordDto keyword,

        @Schema(description = "챌린지 날짜", example = "2026-03-12")
        LocalDate challengeDate,

        @Schema(description = "시작 시각")
        LocalDateTime startAt,

        @Schema(description = "종료 시각")
        LocalDateTime endAt,

        @Schema(description = "챌린지 상태", example = "OPEN")
        ChallengeStatus status,

        @Schema(description = "참여자 수 (COMPLETED 전에는 0)", example = "15")
        int participantCount,

        @Schema(description = "내 참여 정보 (미참여면 null)")
        MyParticipation myParticipation,

        @Schema(description = "안내 메시지 (SCHEDULED면 '22:00에 시작됩니다.')", example = "22:00에 시작됩니다.")
        String message
) {
    @Builder
    public record KeywordDto(
            @Schema(description = "키워드 ID", example = "1")
            Long id,

            @Schema(description = "키워드 이름", example = "자기소개")
            String name
    ) {}

    /**
     * 내 참여 정보
     * - null: 미참여
     * - score/rank: COMPLETED 전 null
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyParticipation(
            @Schema(description = "참여 ID", example = "1")
            Long attemptId,

            @Schema(description = "참여 상태", example = "UPLOADED")
            ChallengeAttemptStatus status,

            @Schema(description = "점수 (COMPLETED 전 null)", example = "85")
            Integer score,

            @Schema(description = "순위 (COMPLETED 전 null)", example = "3")
            Integer rank
    ) {}
}