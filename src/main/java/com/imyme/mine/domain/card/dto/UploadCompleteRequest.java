package com.imyme.mine.domain.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 오디오 업로드 완료 요청 DTO
 * - 업로드 완료 알림용 최소 필드만 수신
 */
public record UploadCompleteRequest(

    @NotBlank(message = "오디오 Object Key는 필수입니다.")
    String objectKey,

    @Positive(message = "재생 시간은 양수여야 합니다.")
    Integer durationSeconds
) {}
