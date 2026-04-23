package com.imyme.mine.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 서버 에러 응답 DTO
 * - AI 서버의 에러 응답을 구조화하여 파싱
 * - String contains 대신 errorCode 필드를 통해 정확한 에러 매칭
 */
public record AiServerErrorResponse(
    @JsonProperty("error_code")
    String errorCode,

    @JsonProperty("message")
    String message
) {
}