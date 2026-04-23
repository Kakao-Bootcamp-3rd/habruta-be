package com.imyme.mine.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Refresh Token 재사용 시도를 감지하여 토큰 탈취를 방지하는 서비스
 * - 이미 사용된 토큰(Revoked/Rotated)이 다시 사용되면 보안 경고를 발생시킴
 *
 * 동작 원리 (RTR 기반 탈취 감지):
 * 1. 정상 사용자가 토큰 A로 갱신 요청 → 토큰 B 발급 (A는 DB에서 삭제됨)
 * 2. 공격자가 탈취한 토큰 A로 갱신 시도 → DB에서 찾을 수 없음 → 탈취 의심!
 * 3. 또는, 정상 사용자가 토큰 B를 사용한 후 공격자가 토큰 A 사용 → 동일하게 감지됨
 *
 * 대안 고려사항:
 * - OAuth 2.0 RFC 6749 권장사항: 재사용 감지 시 해당 사용자의 모든 세션 무효화
 * - 현재 구현: 경고 로그만 남기고 요청은 거부 (점진적 강화 전략)
 * - 향후 확장: Slack 알림, 관리자 대시보드 연동, 사용자 계정 일시 잠금 등
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenTheftDetector {

    // 유효하지 않은 Refresh Token 사용 시도 감지
    public void detectSuspiciousTokenUsage(String attemptedToken, String reason, Long userId) {
        // 토큰 전체를 로깅하지 않고, 앞 8자만 로깅 (보안)
        // 이유: 로그가 유출되어도 원본 토큰을 복원할 수 없도록 최소 정보만 기록
        String tokenPrefix = attemptedToken != null && attemptedToken.length() >= 8
                ? attemptedToken.substring(0, 8) + "..."
                : "null";

        // 경고 레벨 로그: 모니터링 도구(Sentry, CloudWatch 등)에서 알람 설정 가능
        log.warn(
                "[SECURITY] Suspicious token usage detected. " +
                "Reason: {}, UserId: {}, TokenPrefix: {}, Timestamp: {}",
                reason,
                userId != null ? userId : "unknown",
                tokenPrefix,
                System.currentTimeMillis()
        );

        // TODO: 추후 확장 가능한 부분
        // 1. 보안팀 알림 전송 (Slack, Email 등)
        // sendSecurityAlert(userId, reason);
        //
        // 2. 해당 사용자의 모든 세션 무효화 (RFC 6749 권장)
        // if (userId != null) {
        //     userSessionRepository.deleteAllByUserId(userId);
        //     log.warn("[SECURITY] All sessions invalidated for user: {}", userId);
        // }
        //
        // 3. Rate Limiting 강화 (동일 IP에서 반복 시도 차단)
        // rateLimiter.blockIpTemporarily(ipAddress);
        //
        // 4. 관리자 대시보드에 의심 활동 기록
        // securityDashboard.recordSuspiciousActivity(userId, reason, timestamp);
    }

    // 토큰 갱신 성공 시 정상 사용 기록 (선택적)
    public void recordSuccessfulRefresh(Long userId) {
        // TODO: 추후 구현
        // - Redis에 사용자별 갱신 이력 저장 (TTL 7일)
        // - 갱신 빈도가 비정상적으로 높으면 경고 (예: 1분에 10회 이상)
    }
}
