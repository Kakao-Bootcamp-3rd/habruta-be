package com.imyme.mine.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 기기 등록 요청 DTO
 * - FCM 토큰 등록 및 기기 정보 업데이트
 */
public record RegisterDeviceRequest(
    @NotBlank(message = "Device UUID는 필수입니다.")
    @Size(max = 36, message = "Device UUID는 36자 이하여야 합니다.")
    String deviceUuid,

    @Size(max = 500, message = "FCM 토큰은 500자 이하여야 합니다.")
    String fcmToken,

    @NotBlank(message = "Agent Type은 필수입니다.")
    String agentType,

    @NotBlank(message = "Platform Type은 필수입니다.")
    String platformType,

    Boolean isStandalone,
    Boolean isPushEnabled
) {
}
