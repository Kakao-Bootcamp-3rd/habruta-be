package com.imyme.mine.domain.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PresignedUrlRequest(

    @NotNull(message = "시도 ID는 필수입니다.")
    Long attemptId,

    @NotBlank(message = "콘텐츠 타입은 필수입니다.")
    @Pattern(
        regexp = "^(audio/(mpeg|wav|webm|mp4|m4a))(;.*)?$",
        message = "지원하지 않는 오디오 형식입니다."
    )
    String contentType

) {}
