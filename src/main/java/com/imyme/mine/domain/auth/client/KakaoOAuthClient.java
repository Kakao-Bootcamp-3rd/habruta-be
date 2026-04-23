package com.imyme.mine.domain.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.config.KakaoOAuthProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 카카오 OAuth API 클라이언트
 * - 카카오 액세스 토큰 교환
 * - 사용자 정보 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final KakaoOAuthProperties properties;
    private final RestTemplate restTemplate;

    // Authorization Code로 Access Token(KAKAO) 교환
    public KakaoTokenResponse getAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("code", code);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<KakaoTokenResponse> response = restTemplate.postForEntity(
                properties.getTokenUri(),
                request,
                KakaoTokenResponse.class
            );

            if (response.getBody() == null) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }

            return response.getBody();

        } catch (HttpClientErrorException e) {
            // 400/401 에러 - 잘못된 인증 코드 또는 리다이렉트 URI
            log.error("Invalid Kakao authorization code or redirect URI: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_OAUTH_CODE);
        } catch (RestClientException e) {
            // 네트워크 오류, 타임아웃 등
            log.error("Failed to connect to Kakao OAuth server: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        } catch (Exception e) {
            // 기타 예상치 못한 오류
            log.error("Unexpected error during Kakao token exchange: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    // Access Token(KAKAO)으로 사용자 정보 조회
    public KakaoUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                properties.getUserInfoUri(),
                HttpMethod.GET,
                request,
                KakaoUserInfo.class
            );

            if (response.getBody() == null) {
                throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }

            return response.getBody();

        } catch (HttpClientErrorException e) {
            // 401 에러 - 유효하지 않은 액세스 토큰
            log.error("Invalid or expired Kakao access token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        } catch (RestClientException e) {
            // 네트워크 오류, 타임아웃 등
            log.error("Failed to connect to Kakao user info API: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        } catch (Exception e) {
            // 기타 예상치 못한 오류
            log.error("Unexpected error while fetching Kakao user info: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    // 카카오 토큰 응답
    @Getter
    public static class KakaoTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("expires_in")
        private Integer expiresIn;

        @JsonProperty("scope")
        private String scope;
    }

    // 카카오 사용자 정보 응답
    @Getter
    public static class KakaoUserInfo {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("kakao_account")
        private KakaoAccount kakaoAccount;

        @Getter
        public static class KakaoAccount {
            @JsonProperty("profile")
            private Profile profile;

            @JsonProperty("email")
            private String email;

            @Getter
            public static class Profile {
                @JsonProperty("nickname")
                private String nickname;

                @JsonProperty("profile_image_url")
                private String profileImageUrl;
            }
        }
    }
}
