package com.imyme.mine.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.error.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 실패 시 401 Unauthorized 응답 처리
 * - 인증되지 않은 사용자가 보호된 리소스에 접근할 때 호출
 * - JWT 필터에서 담아준 예외 속성("exception")을 확인하여 구체적인 에러 사유를 응답함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        log.error("Unauthorized error: {}", authException.getMessage());

        String exceptionCode = (String) request.getAttribute("exception");
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        if (exceptionCode != null) {
            if (exceptionCode.equals(ErrorCode.TOKEN_EXPIRED.getCode())) {
                errorCode = ErrorCode.TOKEN_EXPIRED; // 토큰 만료
                log.warn("JWT 인증 실패: Token Expired");
            } else if (exceptionCode.equals(ErrorCode.INVALID_TOKEN.getCode())) {
                errorCode = ErrorCode.INVALID_TOKEN; // 잘못된 토큰
                log.warn("JWT 인증 실패: Invalid Token");
            }
        } else {
            // 필터를 거치지 않고 시큐리티단에서 바로 막힌 경우 (예: 헤더 없음)
            log.error("Unauthorized error: {}", authException.getMessage());
        }

        // 401 Unauthorized 응답
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(
                errorCode,
                request.getRequestURI()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
