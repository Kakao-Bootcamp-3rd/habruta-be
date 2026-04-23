package com.imyme.mine.domain.auth.dto;

import lombok.Builder;

@Builder
public record TokenRefreshResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn
) {}
