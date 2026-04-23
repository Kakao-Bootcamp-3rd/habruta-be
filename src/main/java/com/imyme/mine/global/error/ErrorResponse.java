package com.imyme.mine.global.error;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * 에러 응답 DTO
 * - API 명세서 형식 준수
 * - details는 선택적 (null이면 JSON에서 제외)
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 JSON에서 제외
public class ErrorResponse {

    private final String error; // 에러 코드 (UNAUTHORIZED, USER_NOT_FOUND)
    private final String message; // 사용자에게 표시할 메시지
    private final Map<String, Object> details; // 상세 정보 (선택적)
    private final LocalDateTime timestamp; // 에러 발생 시각
    private final String path; // 요청 경로

    // 기본 에러 응답 생성
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return ErrorResponse.builder()
                .error(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }

    // 상세 정보 포함 에러 응답 생성
    public static ErrorResponse of(ErrorCode errorCode, String path, Map<String, Object> details) {
        return ErrorResponse.builder()
                .error(errorCode.getCode())
                .message(errorCode.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }

    // 커스텀 메시지 에러 응답 생성
    public static ErrorResponse of(ErrorCode errorCode, String path, String customMessage) {
        return ErrorResponse.builder()
                .error(errorCode.getCode())
                .message(customMessage)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
}
