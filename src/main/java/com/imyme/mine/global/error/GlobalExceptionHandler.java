package com.imyme.mine.global.error;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 전역 예외 처리 핸들러
 * - 모든 예외를 ErrorResponse 형식으로 변환
 * - 에러 로그 기록
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========== 비즈니스 예외 ==========
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Business Exception: {} - {}", e.getErrorCode(), e.getMessage());

        ErrorResponse response = e.getDetails() != null
                ? ErrorResponse.of(e.getErrorCode(), request.getRequestURI(), e.getDetails())
                : ErrorResponse.of(e.getErrorCode(), request.getRequestURI());

        return ResponseEntity.status(e.getErrorCode().getStatus()).body(response);
    }

    // ========== Spring Security 인증 예외 ==========
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Authentication Exception: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.UNAUTHORIZED, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // ========== Spring Security 인가 예외 ==========
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Access Denied: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.FORBIDDEN, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // ========== Validation 예외 (DTO) ==========
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Validation Exception: {}", e.getMessage());

        // 첫 번째 에러만 사용
        FieldError fieldError = e.getBindingResult().getFieldErrors().getFirst();

        Map<String, Object> details = new HashMap<>();
        details.put("field", fieldError.getField());
        details.put("reason", fieldError.getDefaultMessage());
        details.put("rejectedValue", fieldError.getRejectedValue());

        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_FAILED, request.getRequestURI(), details);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    // ========== Validation 예외 (PathVariable, RequestParam) ==========
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Constraint Violation: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_FAILED, request.getRequestURI(), e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ========== 잘못된 타입 파라미터 ==========
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Type Mismatch: {} - expected {}", e.getName(), e.getRequiredType());

        Map<String, Object> details = new HashMap<>();
        details.put("field", e.getName());
        details.put("reason", "타입이 일치하지 않습니다");
        details.put("expectedType", Objects.requireNonNull(e.getRequiredType()).getSimpleName());

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_REQUEST, request.getRequestURI(), details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ========== 필수 파라미터 누락 ==========
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Missing Parameter: {}", e.getParameterName());

        Map<String, Object> details = new HashMap<>();
        details.put("field", e.getParameterName());
        details.put("reason", "필수 파라미터가 누락되었습니다");

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_REQUEST, request.getRequestURI(), details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ========== 잘못된 JSON 형식 ==========
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Invalid JSON: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_REQUEST, request.getRequestURI(), "잘못된 JSON 형식입니다");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ========== 지원하지 않는 HTTP 메서드 ==========
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("Method Not Supported: {} for {}", e.getMethod(), request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INVALID_REQUEST, request.getRequestURI(), "지원하지 않는 HTTP 메서드입니다: " + e.getMethod());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    // ========== 404 Not Found ==========
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.warn("No Handler Found: {}", request.getRequestURI());

        ErrorResponse response = ErrorResponse.of(ErrorCode.NOT_FOUND, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        MDC.put("exception", e.getClass().getSimpleName());
        log.error("Unexpected Exception: ", e);

        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
