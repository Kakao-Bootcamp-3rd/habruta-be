package com.imyme.mine.domain.ai.dto;

/**
 * AI 서버 STT API 요청 DTO
 * POST /api/v1/transcriptions
 */
public record TranscriptionRequest(
    String audioUrl
) {
}