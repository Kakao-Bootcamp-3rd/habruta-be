package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그아웃 서비스
 * - UserSessions Hard Delete (Refresh Token 무효화)
 * - Device는 재로그인 시 재사용을 위해 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final UserSessionRepository userSessionRepository;

    @Transactional
    public void logout(Long userId, String deviceUuid) {
        log.info("로그아웃 시작: userId={}, deviceUuid={}", userId, deviceUuid);

        if (userSessionRepository.findByUserIdAndDeviceUuid(userId, deviceUuid).isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        // UserSession만 삭제 (Refresh Token 무효화)
        // Device는 삭제하지 않음 (재로그인 시 재사용)
        userSessionRepository.deleteByUserIdAndDeviceUuid(userId, deviceUuid);

        log.info("로그아웃 완료 - UserSession 삭제, Device 유지");
    }
}
