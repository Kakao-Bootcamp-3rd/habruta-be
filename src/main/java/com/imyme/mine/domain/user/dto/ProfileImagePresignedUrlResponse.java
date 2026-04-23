package com.imyme.mine.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 프로필 이미지 Presigned URL 응답 DTO
 * - 클라이언트가 S3에 직접 업로드할 수 있는 정보 제공
 */
@Schema(description = "프로필 이미지 Presigned URL 응답")
public record ProfileImagePresignedUrlResponse(

    @Schema(description = "S3 업로드용 서명된 URL (PUT 메서드 사용)", example = "https://...")
    String uploadUrl,

    @Schema(description = "S3 Object Key", example = "profiles/1/550e8400-e29b-41d4-a716-446655440000.jpg")
    String profileImageKey,

    @Schema(description = "URL 유효기간 (초)", example = "300")
    Integer expiresIn,

    @Schema(description = "업로드 제약사항")
    Constraints constraints

) {

    public static ProfileImagePresignedUrlResponse of(
        String uploadUrl,
        String profileImageKey,
        Integer expiresIn,
        Long maxSizeBytes,
        List<String> allowedContentTypes
    ) {
        Constraints constraints = new Constraints(maxSizeBytes, allowedContentTypes);
        return new ProfileImagePresignedUrlResponse(
            uploadUrl,
            profileImageKey,
            expiresIn,
            constraints
        );
    }

    @Schema(description = "업로드 제약사항")
    public record Constraints(
        @Schema(description = "최대 파일 크기 (바이트)", example = "5242880")
        Long maxSizeBytes,

        @Schema(description = "허용된 Content-Type 목록", example = "[\"image/jpeg\", \"image/png\", \"image/heic\", \"image/webp\"]")
        List<String> allowedContentTypes
    ) {}
}
