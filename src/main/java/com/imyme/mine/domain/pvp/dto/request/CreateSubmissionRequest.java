package com.imyme.mine.domain.pvp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 녹음 제출 URL 발급 요청 (4.5)
 */
public record CreateSubmissionRequest(
        @Schema(description = "파일 이름", example = "recording.webm")
        @NotBlank(message = "파일명을 입력해주세요")
        String fileName,

        @Schema(description = "파일 MIME 타입", example = "audio/webm")
        @NotBlank(message = "Content-Type을 입력해주세요")
        String contentType,

        @Schema(description = "파일 크기 (바이트)", example = "1048576")
        @NotNull(message = "파일 크기를 입력해주세요")
        @Positive(message = "파일 크기는 양수여야 합니다")
        Long fileSize
) {
}
