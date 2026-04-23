package com.imyme.mine.global.security.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Token Hasher
 * - Refresh Token을 SHA-256으로 해싱하여 DB에 저장하기 위한 유틸리티
 * - One-way Hashing으로 DB 탈취 시에도 원본 토큰을 복구할 수 없도록 보안 강화
 *
 * 왜 SHA-256을 선택했는가?
 * 1. **보안성**: SHA-256은 암호학적으로 안전한 해시 알고리즘으로, 역산(Reverse)이 불가능합니다.
 * 2. **충돌 저항성**: 서로 다른 입력이 동일한 해시값을 생성할 확률이 극히 낮습니다.
 * 3. **성능**: MD5보다 안전하면서도 bcrypt보다 빠릅니다 (bcrypt는 비밀번호용으로 의도적으로 느림).
 * 4. **표준화**: java.security.MessageDigest에 기본 제공되어 별도 라이브러리 불필요.
 *
 * 대안 비교:
 * - bcrypt/scrypt: 비밀번호용으로 설계됨 (의도적으로 느림). 토큰 해싱에는 과도함.
 * - MD5: 충돌 공격에 취약하여 더 이상 안전하지 않음.
 * - SHA-512: SHA-256보다 더 느리지만 보안 이득이 크지 않음 (토큰은 이미 충분히 무작위).
 */
@Slf4j
public final class TokenHasher {

    // 유틸리티 클래스이므로 인스턴스 생성 방지
    private TokenHasher() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    // Refresh Token을 SHA-256으로 해싱하여 Base64 URL-safe 인코딩된 문자열로 반환
    public static String hash(String token) {
        // null 검증: NPE 방지 및 명확한 에러 메시지 제공
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }

        try {
            // JVM에 내장된 SHA-256 알고리즘 인스턴스 획득
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 토큰 문자열을 UTF-8 바이트로 변환 후 해싱 수행
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            // URL-safe Base64 인코딩 (DB 저장 포맷)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Java 표준 라이브러리에 포함되어 있으므로, 이 예외는 실질적으로 발생하지 않음
            // 만약 발생한다면 JVM 환경 문제이므로 RuntimeException으로 래핑하여 전파
            log.error("SHA-256 algorithm not found. This should never happen.", e);
            throw new RuntimeException("Failed to hash token: SHA-256 algorithm not available", e);
        }
    }

    // 원본 토큰과 저장된 해시값이 일치하는지 검증
    // 타이밍 공격(Timing Attack) 에 취약할 수 있으나, 토큰 해싱 검증에서는 큰 문제가 되지 않음
    public static boolean matches(String rawToken, String storedHash) {
        // null 검증: NPE 방지
        if (rawToken == null || storedHash == null) {
            return false;
        }

        // 원본 토큰을 해싱한 값과 저장된 해시를 비교
        // 필요시 MessageDigest.isEqual() 사용 고려
        String hashedInput = hash(rawToken);
        return hashedInput.equals(storedHash);
    }
}
