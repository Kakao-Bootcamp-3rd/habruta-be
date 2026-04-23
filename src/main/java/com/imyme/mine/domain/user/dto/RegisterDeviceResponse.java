package com.imyme.mine.domain.user.dto;

import com.imyme.mine.domain.auth.entity.AgentType;
import com.imyme.mine.domain.auth.entity.Device;
import com.imyme.mine.domain.auth.entity.PlatformType;

import java.time.LocalDateTime;

/**
 * 기기 등록 응답 DTO
 */
public record RegisterDeviceResponse(
    String deviceUuid,
    String fcmToken,
    AgentType agentType,
    PlatformType platformType,
    boolean isStandalone,
    boolean isPushEnabled,
    LocalDateTime lastActiveAt
) {
    public static RegisterDeviceResponse from(Device device) {
        return new RegisterDeviceResponse(
            device.getDeviceUuid(),
            device.getFcmToken(),
            device.getAgentType(),
            device.getPlatformType(),
            device.isStandalone(),
            device.isPushEnabled(),
            device.getLastActiveAt()
        );
    }
}
