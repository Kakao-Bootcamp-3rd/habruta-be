package com.imyme.mine.domain.pvp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 방 생성 요청 (4.2)
 */
public record CreateRoomRequest(
        @Schema(description = "카테고리 ID", example = "1")
        @NotNull(message = "카테고리를 선택해주세요")
        Long categoryId,

        @Schema(description = "방 이름 (2~30자, 금지어 포함 시 거절)", example = "면접 연습방")
        @NotBlank(message = "방 이름을 입력해주세요")
        @Size(min = 2, max = 30, message = "방 이름은 2~30자 사이여야 합니다")
        String roomName
) {
}
