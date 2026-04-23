package com.imyme.mine.domain.ai.dto;

import lombok.Getter;

/**
 * AI 서버 STT API 응답 DTO
 * POST /api/v1/transcriptions
 *
 * 응답 예시:
 * {
 *   "success": true,
 *   "data": {
 *     "text": "프로세스는 실행 중인 프로그램입니다..."
 *   },
 *   "error": null
 * }
 */
@Getter
public class TranscriptionResponse {

    private Boolean success;
    private Data data;
    private String error;

    @Getter
    public static class Data {
        private String text;
    }
}