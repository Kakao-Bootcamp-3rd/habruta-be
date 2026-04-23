package com.imyme.mine.domain.challenge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * 챌린지 랭킹 조회 응답 (GET /challenges/{id}/rankings)
 */
@Builder
public record ChallengeRankingResponse(
        @Schema(description = "챌린지 정보")
        ChallengeInfo challenge,

        @Schema(description = "랭킹 목록")
        List<RankingItem> rankings,

        @Schema(description = "페이지네이션 정보")
        Pagination pagination,

        @Schema(description = "내 순위 (미참여면 null)")
        MyRank myRank
) {
    @Builder
    public record ChallengeInfo(
            @Schema(description = "챌린지 ID", example = "1")
            Long id,

            @Schema(description = "키워드 이름", example = "자기소개")
            String keywordName,

            @Schema(description = "챌린지 날짜", example = "2026-03-12")
            LocalDate challengeDate,

            @Schema(description = "챌린지 상태", example = "COMPLETED")
            ChallengeStatus status,

            @Schema(description = "참여자 수", example = "15")
            int participantCount
    ) {}

    @Builder
    public record RankingItem(
            @Schema(description = "순위", example = "1")
            Integer rank,

            @Schema(description = "유저 ID", example = "42")
            Long userId,

            @Schema(description = "닉네임", example = "홍길동")
            String nickname,

            @Schema(description = "프로필 이미지 URL")
            String profileImageUrl,

            @Schema(description = "점수", example = "95")
            Integer score,

            @Schema(description = "본인 여부", example = "false")
            boolean isMe
    ) {}

    @Builder
    public record Pagination(
            @Schema(description = "현재 페이지 (1부터)", example = "1")
            int currentPage,

            @Schema(description = "전체 페이지 수", example = "3")
            int totalPages,

            @Schema(description = "전체 랭킹 수", example = "45")
            long totalCount,

            @Schema(description = "페이지 크기", example = "20")
            int size,

            @Schema(description = "다음 페이지 존재 여부", example = "true")
            boolean hasNext,

            @Schema(description = "이전 페이지 존재 여부", example = "false")
            boolean hasPrevious
    ) {}

    /**
     * 내 순위 정보 (참여하지 않았으면 null)
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyRank(
            @Schema(description = "내 순위", example = "5")
            Integer rank,

            @Schema(description = "내 점수", example = "82")
            Integer score,

            @Schema(description = "상위 퍼센트", example = "66.7")
            Double percentile
    ) {}
}
