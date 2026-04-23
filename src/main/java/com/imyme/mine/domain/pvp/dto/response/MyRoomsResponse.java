package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 PvP 기록 조회 응답 (4.8)
 */
@Getter
@AllArgsConstructor
@Builder
public class MyRoomsResponse {
    @Schema(description = "기록 목록")
    private List<HistoryItem> histories;

    @Schema(description = "페이지네이션 메타")
    private PageMeta meta;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class HistoryItem {
        @Schema(description = "기록 ID", example = "1")
        private Long historyId;

        @Schema(description = "방 정보")
        private RoomInfo room;

        @Schema(description = "카테고리 정보")
        private CategoryInfo category;

        @Schema(description = "키워드 정보")
        private KeywordInfo keyword;

        @Schema(description = "내 역할 (HOST/GUEST)", example = "HOST")
        private PvpRole myRole;

        @Schema(description = "내 결과")
        private MyResult myResult;

        @Schema(description = "상대방 정보")
        private OpponentInfo opponent;

        @Schema(description = "기록 숨김 여부", example = "false")
        private Boolean isHidden;

        @Schema(description = "대결 종료 시각")
        private LocalDateTime finishedAt;
    }

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
    public static class MyResult {
        @Schema(description = "점수", example = "85")
        private Integer score;

        @Schema(description = "레벨", example = "5")
        private Integer level;

        @Schema(description = "승리 여부", example = "true")
        private Boolean isWinner;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class OpponentInfo {
        @Schema(description = "상대방 유저 ID", example = "43")
        private Long id;

        @Schema(description = "상대방 닉네임", example = "김철수")
        private String nickname;

        @Schema(description = "상대방 프로필 이미지 URL")
        private String profileImageUrl;

        @Schema(description = "상대방 레벨", example = "4")
        private Integer level;

        @Schema(description = "상대방 점수", example = "72")
        private Integer score;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class PageMeta {
        @Schema(description = "현재 페이지 항목 수", example = "20")
        private Integer size;

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private Boolean hasNext;

        @Schema(description = "다음 페이지 커서")
        private String nextCursor;
    }
}