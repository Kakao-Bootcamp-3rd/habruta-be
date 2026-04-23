package com.imyme.mine.domain.pvp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * PvP 결과 조회 응답 (4.7)
 */
@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomResultResponse {
    @Schema(description = "방 정보")
    private RoomInfo room;

    @Schema(description = "카테고리 정보")
    private CategoryInfo category;

    @Schema(description = "키워드 정보")
    private KeywordInfo keyword;

    @Schema(description = "방 상태", example = "FINISHED")
    private PvpRoomStatus status;

    @Schema(description = "내 결과")
    private PlayerResult myResult;

    @Schema(description = "상대방 결과")
    private PlayerResult opponentResult;

    @Schema(description = "승자 정보 (무승부 시 null)")
    private UserInfo winner;

    @Schema(description = "대결 종료 시각")
    private LocalDateTime finishedAt;

    @Schema(description = "안내 메시지 (PROCESSING 시 분석 중 메시지)")
    private String message;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class RoomInfo {
        @Schema(description = "방 ID", example = "1")
        private Long id;

        @Schema(description = "방 이름", example = "면접 연습방")
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class CategoryInfo {
        @Schema(description = "카테고리 ID", example = "1")
        private Long id;

        @Schema(description = "카테고리 이름", example = "면접")
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class KeywordInfo {
        @Schema(description = "키워드 ID", example = "1")
        private Long id;

        @Schema(description = "키워드 이름", example = "자기소개")
        private String name;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class PlayerResult {
        @Schema(description = "기록 ID", example = "1")
        private Long historyId;

        @Schema(description = "기록 숨김 여부", example = "false")
        private Boolean isHidden;

        @Schema(description = "유저 정보")
        private UserInfo user;

        @Schema(description = "점수", example = "85")
        private Integer score;

        @Schema(description = "녹음 파일 URL")
        private String audioUrl;

        @Schema(description = "녹음 길이 (초)", example = "45")
        private Integer durationSeconds;

        @Schema(description = "STT 변환 텍스트")
        private String sttText;

        @Schema(description = "AI 피드백")
        private FeedbackDetail feedback;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        @Schema(description = "유저 ID", example = "42")
        private Long id;

        @Schema(description = "닉네임", example = "홍길동")
        private String nickname;

        @Schema(description = "프로필 이미지 URL")
        private String profileImageUrl;

        @Schema(description = "레벨", example = "5")
        private Integer level;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class FeedbackDetail {
        @Schema(description = "피드백 요약")
        private String summary;

        @Schema(description = "핵심 키워드 목록")
        private java.util.List<String> keywords;

        @Schema(description = "사실 관계 분석")
        private String facts;

        @Schema(description = "이해도 분석")
        private String understanding;

        @Schema(description = "개인화 피드백")
        private String personalizedFeedback;
    }
}