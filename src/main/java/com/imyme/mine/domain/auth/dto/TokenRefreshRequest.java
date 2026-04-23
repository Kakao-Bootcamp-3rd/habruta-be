package com.imyme.mine.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
    @NotBlank(message = "Refresh token은 필수입니다.")
    String refreshToken
) {}
