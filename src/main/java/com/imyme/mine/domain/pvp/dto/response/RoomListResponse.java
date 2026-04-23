package com.imyme.mine.domain.pvp.dto.response;

import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방 목록 조회 응답 (4.1)
 */
@Getter
@AllArgsConstructor
@Builder
public class RoomListResponse {
    @Schema(description = "방 목록")
    private List<RoomItem> rooms;

    @Schema(description = "페이지네이션 메타")
    private PageMeta meta;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class RoomItem {
        @Schema(description = "방 정보")
        private RoomInfo room;

        @Schema(description = "카테고리 정보")
        private CategoryInfo category;

        @Schema(description = "방 상태", example = "OPEN")
        private PvpRoomStatus status;

        @Schema(description = "호스트 정보")
        private UserInfo host;

        @Schema(description = "게스트 정보 (미입장 시 null)")
        private UserInfo guest;

        @Schema(description = "방 생성 시각")
        private LocalDateTime createdAt;
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
    public static class PageMeta {
        @Schema(description = "현재 페이지 항목 수", example = "20")
        private Integer size;

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private Boolean hasNext;

        @Schema(description = "다음 페이지 커서")
        private String nextCursor;
    }
}
