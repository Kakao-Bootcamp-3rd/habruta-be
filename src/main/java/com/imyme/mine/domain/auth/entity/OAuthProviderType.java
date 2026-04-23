package com.imyme.mine.domain.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OAuth 제공자 타입
 */
@Getter
@RequiredArgsConstructor
public enum OAuthProviderType {
    KAKAO("kakao", "카카오"),
    GOOGLE("google", "구글"),
    APPLE("apple", "애플");

    private final String code;
    private final String displayName;

    // 문자열로부터 OAuthProvider 찾기
    public static OAuthProviderType fromCode(String code) {
        for (OAuthProviderType provider : values()) {
            if (provider.getCode().equalsIgnoreCase(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Invalid OAuth provider: " + code);
    }

    // 문자열이 유효한 OAuth provider인지 확인
    public static boolean isValid(String code) {
        try {
            fromCode(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
