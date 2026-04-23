package com.imyme.mine.domain.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SSE 스트림 토큰 발급 응답")
public record StreamTokenResponse(

    @Schema(description = "1회용 SSE 스트림 토큰. 30초 유효하며 한 번만 사용 가능합니다.",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String token

) {
    public static StreamTokenResponse of(String token) {
        return new StreamTokenResponse(token);
    }
}