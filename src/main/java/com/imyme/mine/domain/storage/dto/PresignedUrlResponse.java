package com.imyme.mine.domain.storage.dto;

import java.time.LocalDateTime;

public record PresignedUrlResponse(

    Long attemptId,
    String uploadUrl,
    String contentType,
    String objectKey,
    LocalDateTime expiresAt

) {

    public static PresignedUrlResponse of(
        Long attemptId,
        String uploadUrl,
        String contentType,
        String objectKey,
        LocalDateTime expiresAt
    ) {
        return new PresignedUrlResponse(attemptId, uploadUrl, contentType, objectKey, expiresAt);
    }
}
