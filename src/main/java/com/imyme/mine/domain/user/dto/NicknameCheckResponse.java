package com.imyme.mine.domain.user.dto;

/**
 * 닉네임 중복 확인 응답 DTO
 */
public record NicknameCheckResponse(
    boolean available,
    String reason
) {
    public static NicknameCheckResponse ofAvailable() {
        return new NicknameCheckResponse(true, null);
    }

    public static NicknameCheckResponse ofUnavailable(ReasonCode code) {
        return new NicknameCheckResponse(false, code.name());
    }

    public enum ReasonCode {
        DUPLICATE,          // 중복됨
        FORBIDDEN_WORD,     // 금지어 포함
        INVALID_FORMAT,     // 형식 안 맞음 (길이, 특수문자 등)
        INVALID_LENGTH      // 길이 제한 위반 (명세 추가 추천)
    }
}
