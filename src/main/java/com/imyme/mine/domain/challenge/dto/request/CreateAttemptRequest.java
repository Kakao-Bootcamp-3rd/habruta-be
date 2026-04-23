package com.imyme.mine.domain.challenge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 챌린지 참여 시작 요청 (POST /challenges/{challengeId}/attempts)
 * - fileSize는 녹음 전 알 수 없으므로 받지 않음 — upload-complete 시 HeadObject로 검증
 */
public record CreateAttemptRequest(
        @Schema(description = "파일 MIME 타입 (audio/webm, audio/mp4, audio/m4a, audio/mpeg, audio/wav)", example = "audio/webm")
        @NotBlank
        @Pattern(regexp = "audio/(webm|mp4|m4a|mpeg|wav)(;.*)?", message = "허용되지 않는 파일 형식입니다.")
        String contentType
) {}