package com.imyme.mine.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LogoutRequest(
    @NotBlank(message = "Device UUID는 필수입니다")
    @Pattern(
        regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        message = "유효한 UUID 형식이 아닙니다"
    )
    String deviceUuid
) {}
