package com.imyme.mine.domain.user.service;

import com.imyme.mine.domain.auth.entity.AgentType;
import com.imyme.mine.domain.auth.entity.Device;
import com.imyme.mine.domain.auth.entity.PlatformType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.domain.user.dto.RegisterDeviceRequest;
import com.imyme.mine.domain.user.dto.RegisterDeviceResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 관리 서비스
 * - FCM 토큰 등록 및 업데이트
 * - 기기 정보 관리 (Upsert)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    /**
     * 기기 등록 또는 업데이트 (Upsert)
     * - 기존 기기: FCM 토큰 및 설정 업데이트 (Dirty Checking)
     * - 신규 기기: 새로운 Device 엔티티 생성
     */
    @Transactional
    public RegisterDeviceResponse registerDevice(Long userId, RegisterDeviceRequest request) {
        log.info("기기 등록 시작: userId={}, deviceUuid={}", userId, request.deviceUuid());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        AgentType agentType = parseAgentType(request.agentType());
        PlatformType platformType = parsePlatformType(request.platformType());

        Device device = deviceRepository.findByDeviceUuid(request.deviceUuid())
                .map(existingDevice -> {
                    // 기존 기기: Dirty Checking으로 업데이트
                    log.info("기존 기기 업데이트: deviceId={}", existingDevice.getId());

                    if (request.fcmToken() != null) {
                        existingDevice.updateFcmToken(request.fcmToken());
                    }

                    if (request.isPushEnabled() != null) {
                        existingDevice.changePushStatus(request.isPushEnabled());
                    }

                    if (request.isStandalone() != null) {
                        existingDevice.updateStandaloneMode(request.isStandalone());
                    }

                    existingDevice.login(user);

                    return existingDevice;
                })
                .orElseGet(() -> {
                    // 신규 기기: 새로운 엔티티 생성
                    log.info("신규 기기 생성: deviceUuid={}", request.deviceUuid());

                    Device newDevice = Device.builder()
                            .deviceUuid(request.deviceUuid())
                            .fcmToken(request.fcmToken())
                            .agentType(agentType)
                            .platformType(platformType)
                            .isStandalone(request.isStandalone() != null ? request.isStandalone() : false)
                            .isPushEnabled(request.isPushEnabled() != null ? request.isPushEnabled() : true)
                            .build();

                    newDevice.login(user);

                    return deviceRepository.save(newDevice);
                });

        log.info("기기 등록 완료: deviceId={}", device.getId());

        return RegisterDeviceResponse.from(device);
    }

    // 기기 삭제 (Soft Delete) : 권한 확인 후 해당 기기의 모든 세션 삭제 및 기기 Soft Delete
    @Transactional
    public void deleteDevice(Long userId, String deviceUuid) {
        Device device = deviceRepository.findByDeviceUuid(deviceUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

        if (device.getLastUser() != null && !device.getLastUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        userSessionRepository.deleteAllByDeviceId(device.getId());
        deviceRepository.softDeleteByDeviceUuid(deviceUuid);
    }

    // AgentType Enum 파싱
    private AgentType parseAgentType(String agentTypeStr) {
        try {
            return AgentType.valueOf(agentTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 AgentType: {}", agentTypeStr);
            throw new BusinessException(ErrorCode.INVALID_AGENT_TYPE);
        }
    }

    // PlatformType Enum 파싱
    private PlatformType parsePlatformType(String platformTypeStr) {
        try {
            return PlatformType.valueOf(platformTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 PlatformType: {}", platformTypeStr);
            throw new BusinessException(ErrorCode.INVALID_PLATFORM_TYPE);
        }
    }
}
