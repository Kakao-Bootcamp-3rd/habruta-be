package com.imyme.mine.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 통일된 API 성공 응답 형식
 * - 프론트엔드가 항상 동일한 구조로 처리 (success 필드로 성공/실패 구분)
 * - 에러 응답과 형식 통일
 * - WebSocket/SSE 응답과 구조 통일
 * - 페이징 메타데이터 추가 용이
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // null 필드는 JSON에서 제외
public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {

    // 성공 응답(데이터만) : 가장 일반적인 케이스 (조회)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    // 성공 응답 - 데이터 + 메시지 : 생성/수정/삭제 시 사용자에게 피드백 필요한 경우
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    // 성공 응답(메시지만) : 204 No Content 대신 200 + 메시지 반환 시
    // TODO : 필요 여부 검토
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, null, message, Instant.now());
    }

    // 성공 응답(데이터만-타임스탬프X) : 단순 조회에서 불필요한 필드 제거
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, null, null);
    }
}
