package com.imyme.mine.global.security.jwt;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.service.AuthSessionCacheService;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.UserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * JWT 인증 필터
 * - Authorization 헤더에서 JWT 토큰을 추출하여 검증
 * - 유효한 토큰인 경우 SecurityContext에 인증 정보 설정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuthSessionCacheService authSessionCacheService;
    private final Tracer tracer;

    // JWT 토큰을 추출하고 검증하여 인증 정보 설정
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Span jwtFilterSpan = startSpan("jwt.filter", request);
        boolean chainInvoked = false;
        try (Tracer.SpanInScope ignored = tracer.withSpan(jwtFilterSpan)) {
            chainInvoked = doAuthenticate(request, response, filterChain);
        } catch (ExpiredJwtException e) {
            jwtFilterSpan.error(e);
            request.setAttribute("exception", ErrorCode.TOKEN_EXPIRED.getCode());
        } catch (JwtException | IllegalArgumentException e) {
            jwtFilterSpan.error(e);
            request.setAttribute("exception", ErrorCode.INVALID_TOKEN.getCode());
        } finally {
            jwtFilterSpan.end();
        }

        if (!chainInvoked) {
            filterChain.doFilter(request, response);
        }
    }

    private boolean doAuthenticate(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws IOException, ServletException {
        try {
            // Authorization 헤더에서 JWT 토큰 추출
            String token = trace("jwt.extract-token", request, () -> getJwtFromRequest(request));

            // 토큰이 존재하고 유효한 경우
            if (StringUtils.hasText(token) && trace("jwt.validate-token", request, () -> jwtTokenProvider.validateToken(token))) {
                // 토큰에서 사용자 ID 추출
                Long userId = trace("jwt.get-user-id", request, () -> jwtTokenProvider.getUserIdFromToken(token));
                String role = trace("jwt.get-role", request, () -> jwtTokenProvider.getRoleFromToken(token));
                String deviceUuid = trace("jwt.get-device-uuid", request, () -> jwtTokenProvider.getDeviceUuidFromToken(token));

                // UserSession 존재 여부 확인을 통한 보안 강화 (로그아웃 여부 체크)
                // 2.46s 지연 발생
                if (!trace("jwt.session.exists", request, () -> hasActiveSession(userId, deviceUuid))) {
                    log.warn("Access denied: No active session found for user {}", userId);
                    request.setAttribute("exception", ErrorCode.SESSION_EXPIRED.getCode());
                    filterChain.doFilter(request, response);
                    return true;  // 인증 실패, 다음 필터로 넘어가지 않음
                }

                // UserPrincipal 생성
                UserPrincipal userPrincipal = trace("jwt.user-principal.create", request, () -> {
                    if (StringUtils.hasText(role)) {
                        return UserPrincipal.fromClaims(userId, role);
                    }

                    User user = trace("jwt.user.find", request, () -> userRepository.findById(userId))
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
                    return UserPrincipal.from(user);
                });

                // Authentication 객체 생성
                UsernamePasswordAuthenticationToken authentication = trace(
                        "jwt.authentication.create",
                        request,
                        () -> new UsernamePasswordAuthenticationToken(
                                userPrincipal,
                                null,
                                userPrincipal.getAuthorities()));

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // SecurityContext에 인증 정보 설정
                trace("jwt.security-context.set", request, () -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    return null;
                });

                log.debug("Set authentication for user: {}", userId);
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute("exception", ErrorCode.TOKEN_EXPIRED.getCode());
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute("exception", ErrorCode.INVALID_TOKEN.getCode());
        }

        return false;
    }

    private boolean hasActiveSession(Long userId, String deviceUuid) {
        if (StringUtils.hasText(deviceUuid)) {
            return authSessionCacheService.hasActiveSession(userId, deviceUuid);
        }

        return authSessionCacheService.hasActiveSession(userId);
    }

    // HTTP 요청의 Authorization 헤더에서 Bearer 토큰 추출
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

    private <T> T trace(String spanName, HttpServletRequest request, Supplier<T> supplier) {
        Span span = startSpan(spanName, request);
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return supplier.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private Span startSpan(String spanName, HttpServletRequest request) {
        return tracer.nextSpan()
                .name(spanName)
                .tag("http.method", request.getMethod())
                .tag("http.route", request.getRequestURI())
                .start();
    }
}
