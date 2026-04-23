package com.imyme.mine.domain.auth.service;

import com.imyme.mine.global.config.OAuthKakaoProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카카오 OAuth Redirect URI Resolver
 * - 클라이언트의 Origin을 분석하여 적절한 환경(local/dev/prod) 판단
 * - 해당 환경에 맞는 Redirect URI 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoRedirectUriResolver {

    private final OAuthKakaoProperties properties;

    /**
     * Origin 헤더를 기반으로 적절한 Redirect URI를 반환
     *
     * @param origin 클라이언트의 Origin 헤더 값 (예: "http://localhost:3000")
     * @return 해당 환경에 맞는 Redirect URI
     * @throws BusinessException Origin이 등록되지 않은 경우
     */
    public String resolveRedirectUri(String origin) {
        if (origin == null || origin.isBlank()) {
            log.warn("Origin 헤더가 비어있습니다. 기본값(local) 사용");
            return properties.getRedirectUris().getLocal();
        }

        // Origin을 환경별로 매칭
        String environment;
        if (origin.equals(properties.getOrigins().getLocal())) {
            environment = "local";
        } else if (origin.equals(properties.getOrigins().getDev())) {
            environment = "dev";
        } else if (origin.equals(properties.getOrigins().getProd())) {
            environment = "prod";
        } else {
            log.error("등록되지 않은 Origin 요청: {}", origin);
            throw new BusinessException(ErrorCode.INVALID_ORIGIN);
        }

        // 환경별 Redirect URI 반환
        String redirectUri = switch (environment) {
            case "local" -> properties.getRedirectUris().getLocal();
            case "dev" -> properties.getRedirectUris().getDev();
            case "prod" -> properties.getRedirectUris().getProd();
            default -> {
                log.error("알 수 없는 환경: {}", environment);
                throw new BusinessException(ErrorCode.INVALID_ENVIRONMENT);
            }
        };

        log.info("Redirect URI 결정 - Origin: {}, Environment: {}, RedirectUri: {}", origin, environment, redirectUri);
        return redirectUri;
    }
}