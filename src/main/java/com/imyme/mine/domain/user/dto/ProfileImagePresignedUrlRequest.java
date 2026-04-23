package com.imyme.mine.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 프로필 이미지 Presigned URL 요청 DTO
 * - 클라이언트가 S3에 직접 업로드하기 위한 서명된 URL 발급 요청
 */
@Schema(description = "프로필 이미지 Presigned URL 발급 요청")
public record ProfileImagePresignedUrlRequest(

    @Schema(
        description = "업로드할 이미지의 Content-Type (MIME 타입)",
        example = "image/jpeg",
        allowableValues = {"image/jpeg", "image/png", "image/heic", "image/webp"}
    )
    @NotBlank(message = "Content-Type은 필수입니다.")
    String contentType

) {}
