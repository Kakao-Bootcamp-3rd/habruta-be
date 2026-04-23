package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 방 상세 응답 (4.2, 4.3, 4.4)
 */
@Getter
@AllArgsConstructor
@Builder
public class RoomResponse {
    @Schema(description = "방 정보")
    private RoomInfo room;

    @Schema(description = "카테고리 정보")
    private CategoryInfo category;

    @Schema(description = "방 상태", example = "OPEN")
    private PvpRoomStatus status;

    @Schema(description = "키워드 정보 (THINKING 이후 노출)")
    private KeywordInfo keyword;

    @Schema(description = "호스트 정보")
    private UserInfo host;

    @Schema(description = "게스트 정보 (미입장 시 null)")
    private UserInfo guest;

    @Schema(description = "방 생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "매칭 시각")
    private LocalDateTime matchedAt;

    @Schema(description = "녹음 시작 시각")
    private LocalDateTime startedAt;

    @Schema(description = "생각 시간 종료 시각")
    private LocalDateTime thinkingEndsAt;

    @Schema(description = "안내 메시지")
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
    public static class KeywordInfo {
        @Schema(description = "키워드 ID", example = "1")
        private Long id;

        @Schema(description = "키워드 이름", example = "자기소개")
        private String name;
    }
}
