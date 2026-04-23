package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.entity.CardFeedback;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 학습 시도 상세 조회 응답 (폴링용)
 * - 상태별로 다른 필드 포함
 * - camelCase 필드명 사용
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptDetailResponse(
    Long attemptId,
    Short attemptNo,
    Long cardId,
    String status,
    AttemptProcessingStep step,
    Integer durationSeconds,
    String sttText,
    FeedbackDto feedback,
    LocalDateTime createdAt,
    LocalDateTime uploadedAt,
    LocalDateTime finishedAt,
    LocalDateTime failedAt,
    LocalDateTime expiresAt,
    LocalDateTime expiredAt,
    LocalDateTime estimatedCompletionAt,
    Integer retryAfterSeconds,
    Long remainingSeconds,
    String errorMessage,
    Boolean retryAvailable,
    String message
) {
    // 업로드 제한 시간 (10분)
    private static final long UPLOAD_LIMIT_MINUTES = 10;

    /**
     * PENDING 상태 응답
     * - S3 업로드 대기 중
     * - expires_at: 10분 후 만료
     * - remaining_seconds: 남은 시간(초)
     */
    public static AttemptDetailResponse fromPending(CardAttempt attempt, LocalDateTime expiresAt) {
        long remainingSeconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();

        return new AttemptDetailResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getCard().getId(),
            attempt.getStatus().name(),
            null,                          // step
            null,                          // durationSeconds
            null,                          // sttText
            null,                          // feedback
            attempt.getCreatedAt(),
            null,                          // uploadedAt
            null,                          // finishedAt
            null,                          // failedAt
            expiresAt,
            null,                          // expiredAt
            null,                          // estimatedCompletionAt
            null,                          // retryAfterSeconds
            Math.max(0, remainingSeconds),
            null,                          // errorMessage
            null,                          // retryAvailable
            "S3 업로드를 완료한 후 upload-complete API를 호출해주세요."
        );
    }

    /**
     * UPLOADED 상태 응답
     * - AI 음성 분석 대기 중
     * - estimated_completion_at: 예상 완료 시간 (업로드 후 3분)
     * - retry_after_seconds: 폴링 간격 (3초)
     */
    public static AttemptDetailResponse fromUploaded(CardAttempt attempt) {
        LocalDateTime estimatedCompletion = attempt.getSubmittedAt().plusMinutes(3);

        return new AttemptDetailResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getCard().getId(),
            attempt.getStatus().name(),
            null,                          // step
            null,                          // durationSeconds
            null,                          // sttText
            null,                          // feedback
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            null,                          // finishedAt
            null,                          // failedAt
            null,                          // expiresAt
            null,                          // expiredAt
            estimatedCompletion,
            3,                             // retryAfterSeconds
            null,                          // remainingSeconds
            null,                          // errorMessage
            null,                          // retryAvailable
            "AI 음성 분석 대기 중입니다."
        );
    }

    /**
     * PROCESSING 상태 응답
     * - AI 피드백 분석 중
     * - estimated_completion_at: 예상 완료 시간
     * - retry_after_seconds: 폴링 간격 (3초)
     */
    public static AttemptDetailResponse fromProcessing(CardAttempt attempt, AttemptProcessingStep step) {
        LocalDateTime estimatedCompletion = attempt.getSubmittedAt().plusMinutes(3);
        String message = (step == AttemptProcessingStep.AUDIO_ANALYSIS)
            ? "AI 음성 분석 중입니다."
            : "AI 피드백 분석 중입니다.";

        return new AttemptDetailResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getCard().getId(),
            attempt.getStatus().name(),
            step,
            null,                          // durationSeconds
            null,                          // sttText
            null,                          // feedback
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            null,                          // finishedAt
            null,                          // failedAt
            null,                          // expiresAt
            null,                          // expiredAt
            estimatedCompletion,
            3,                             // retryAfterSeconds
            null,                          // remainingSeconds
            null,                          // errorMessage
            null,                          // retryAvailable
            message
        );
    }

    /**
     * COMPLETED 상태 응답
     * - 분석 완료
     * - feedback: AI 피드백 포함
     * - stt_text: 음성 텍스트 변환 결과
     */
    public static AttemptDetailResponse fromCompleted(CardAttempt attempt, CardFeedback feedback) {
        return new AttemptDetailResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getCard().getId(),
            attempt.getStatus().name(),
            null,                          // step
            attempt.getDurationSeconds(),
            attempt.getSttText(),
            feedback != null ? FeedbackDto.from(feedback) : null,
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            attempt.getFinishedAt(),
            null,                          // failedAt
            null,                          // expiresAt
            null,                          // expiredAt
            null,                          // estimatedCompletionAt
            null,                          // retryAfterSeconds
            null,                          // remainingSeconds
            null,                          // errorMessage
            null,                          // retryAvailable
            null                           // message
        );
    }

    /**
     * FAILED 상태 응답
     * - 분석 실패
     * - error_message: 실패 사유
     * - retry_available: 재시도 가능 여부
     */
    public static AttemptDetailResponse fromFailed(CardAttempt attempt) {
        return new AttemptDetailResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getCard().getId(),
            attempt.getStatus().name(),
            null,                          // step
            null,                          // durationSeconds
            null,                          // sttText
            null,                          // feedback
            attempt.getCreatedAt(),
            attempt.getSubmittedAt(),
            null,                          // finishedAt
            attempt.getFinishedAt(),       // failedAt
            null,                          // expiresAt
            null,                          // expiredAt
            null,                          // estimatedCompletionAt
            null,                          // retryAfterSeconds
            null,                          // remainingSeconds
            attempt.getErrorMessage() != null ? attempt.getErrorMessage() : "UNKNOWN_ERROR",
            true,                          // retryAvailable
            "오류가 발생했습니다. 다시 시도해주세요."
        );
    }

    /**
     * EXPIRED 상태 응답
     * - 업로드 제한 시간 초과 (10분)
     * - expired_at: 만료 시간
     * - retry_available: 재시도 불가 (처음부터 다시)
     */
    public static AttemptDetailResponse fromExpired(CardAttempt attempt) {
        LocalDateTime calculatedExpiredAt = attempt.getCreatedAt().plusMinutes(UPLOAD_LIMIT_MINUTES);

        return new AttemptDetailResponse(
            attempt.getId(),
            attempt.getAttemptNo(),
            attempt.getCard().getId(),
            attempt.getStatus().name(),
            null,                          // step
            null,                          // durationSeconds
            null,                          // sttText
            null,                          // feedback
            attempt.getCreatedAt(),
            null,                          // uploadedAt
            null,                          // finishedAt
            null,                          // failedAt
            null,                          // expiresAt
            calculatedExpiredAt,           // expiredAt
            null,                          // estimatedCompletionAt
            null,                          // retryAfterSeconds
            null,                          // remainingSeconds
            "업로드 제한 시간이 초과되었습니다.",
            false,                         // retryAvailable
            "처음부터 다시 시도해주세요."
        );
    }
}