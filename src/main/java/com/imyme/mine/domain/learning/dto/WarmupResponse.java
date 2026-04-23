package com.imyme.mine.domain.learning.dto;

/**
 * STT 워밍업 응답 DTO
 */
public record WarmupResponse(
    String message,
    String status
) {
    public static WarmupResponse warmingUp() {
        return new WarmupResponse(
            "STT 서버 워밍업이 시작되었습니다.",
            "WARMING_UP"
        );
    }

    public static WarmupResponse ready() {
        return new WarmupResponse(
            "STT 서버가 이미 준비되어 있습니다.",
            "READY"
        );
    }
}