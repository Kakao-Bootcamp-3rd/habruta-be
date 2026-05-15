package com.imyme.mine.domain.auth.service;

import com.imyme.mine.domain.auth.dto.TokenRefreshRequest;
import com.imyme.mine.domain.auth.dto.TokenRefreshResponse;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.entity.UserSession;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.global.config.JwtProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.jwt.JwtTokenProvider;
import com.imyme.mine.global.security.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 토큰 갱신 서비스
 * - Refresh Token Rotation 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionRepository userSessionRepository;
    private final JwtProperties jwtProperties;
    private final TokenTheftDetector tokenTheftDetector;

    @Transactional
    public TokenRefreshResponse refreshTokens(TokenRefreshRequest request) {
        // 클라이언트가 전송한 Refresh Token (평문)
        String rawRefreshToken = request.refreshToken();

        // JWT 형식 검증 (서명, 만료 시간 등)
        if (!jwtTokenProvider.validateToken(rawRefreshToken)) {
            // JWT가 변조되었거나 만료된 경우 → 탈취 의심 가능
            tokenTheftDetector.detectSuspiciousTokenUsage(
                    rawRefreshToken,
                    "INVALID_JWT_SIGNATURE_OR_EXPIRED",
                    null
            );
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 요청받은 토큰을 해싱하여 DB에서 조회
        String hashedRefreshToken = TokenHasher.hash(rawRefreshToken);

        // 해시값으로 세션 조회
        UserSession userSession = userSessionRepository.findByRefreshTokenWithRelations(hashedRefreshToken)
                .orElseGet(() -> {
                    // 탈취 감지: DB에 없는 토큰 사용 시도
                    tokenTheftDetector.detectSuspiciousTokenUsage(
                            rawRefreshToken,
                            "TOKEN_NOT_FOUND_IN_DB",
                            null  // userId를 알 수 없음 (세션이 없으므로)
                    );
                    throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
                });

        // 세션 만료 여부 확인
        // - 이중 검증: JWT 만료와 DB 만료 모두 통과해야 함
        if (userSession.isExpired()) {
            // 만료된 토큰으로 갱신 시도 → 세션 삭제 후 예외 발생
            Long userId = userSession.getUser().getId();
            userSessionRepository.delete(userSession);

            tokenTheftDetector.detectSuspiciousTokenUsage(
                    rawRefreshToken,
                    "SESSION_EXPIRED",
                    userId
            );
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 정상 검증 완료 → 새 토큰 발급 (RTR 적용)
        User user = userSession.getUser();

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String newRawRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        long expiresIn = jwtProperties.getAccessTokenExpiration() / 1000;
        LocalDateTime newExpiresAt = LocalDateTime.now()
                .plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000);

        // 7새 Refresh Token을 해싱하여 DB 업데이트
        String hashedNewRefreshToken = TokenHasher.hash(newRawRefreshToken);
        userSession.rotateRefreshToken(hashedNewRefreshToken, newExpiresAt);

        log.info("Token refreshed successfully for user: {}", user.getId());

        // 클라이언트에게는 평문 토큰 반환
        return TokenRefreshResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRawRefreshToken)
            .expiresIn(expiresIn)
            .build();
    }
}
