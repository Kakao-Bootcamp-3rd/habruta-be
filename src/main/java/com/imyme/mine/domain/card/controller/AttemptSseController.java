package com.imyme.mine.domain.card.controller;

import com.imyme.mine.domain.card.dto.StreamTokenResponse;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import com.imyme.mine.global.sse.SseService;
import com.imyme.mine.global.sse.SseTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE 스트림 컨트롤러 (솔로 모드 AI 분석 결과 실시간 수신)
 *
 * <p>흐름:
 * 1. POST /stream-token (JWT 인증) → 1회용 토큰 발급 (30초 유효)
 * 2. GET  /stream?token=... (토큰 인증) → SSE 구독 시작
 *    - 분석 완료/실패 시 {@code status-update} 이벤트 수신
 *    - 이미 완료된 경우 즉시 이벤트 전송 (Race Condition 방어)
 */
@Tag(name = "08. Attempt", description = "학습 시도 생성/조회/삭제, 오디오 업로드 완료 처리 API")
@Slf4j
@RestController
@RequestMapping("/cards/{cardId}/attempts/{attemptId}")
@RequiredArgsConstructor
public class AttemptSseController {

    private final SseService sseService;
    private final SseTokenService sseTokenService;

    /**
     * SSE 스트림 접속용 1회용 토큰 발급
     * - JWT 인증 필수 (EventSource는 Authorization 헤더 미지원 → 토큰 방식으로 우회)
     * - 발급된 토큰은 30초 유효하며 1회만 사용 가능
     */
    @Operation(
        summary = "SSE 스트림 토큰 발급",
        description = """
            SSE 구독 전 1회용 토큰을 발급합니다.
            EventSource API는 Authorization 헤더를 지원하지 않으므로, JWT 대신 단기 토큰을 쿼리 파라미터로 전달합니다.
            토큰은 **30초 유효**하며 **1회만 사용** 가능합니다.
            """,
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "토큰 발급 성공",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = StreamTokenResponse.class)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "해당 시도에 대한 접근 권한 없음",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "시도를 찾을 수 없음 - ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/stream-token")
    public ApiResponse<StreamTokenResponse> issueStreamToken(
        @CurrentUser UserPrincipal principal,
        @Parameter(description = "카드 ID", required = true) @PathVariable Long cardId,
        @Parameter(description = "시도 ID", required = true) @PathVariable Long attemptId
    ) {
        String token = sseService.issueStreamToken(principal.getId(), cardId, attemptId);
        log.info("[SSE] 스트림 토큰 발급: userId={}, cardId={}, attemptId={}", principal.getId(), cardId, attemptId);
        return ApiResponse.success(StreamTokenResponse.of(token));
    }

    /**
     * SSE 스트림 구독
     * - 토큰 기반 인증 (JWT 미사용, SecurityConfig에서 permitAll)
     * - 분석 완료/실패 시 {@code status-update} 이벤트 전송
     * - 이미 완료된 시도는 즉시 이벤트 전송 후 종료 (Race Condition 방어)
     */
    @Operation(
        summary = "SSE 스트림 구독",
        description = """
            AI 분석 결과를 SSE(Server-Sent Events)로 실시간 수신합니다.
            `stream-token` API로 발급받은 1회용 토큰을 쿼리 파라미터로 전달하세요.

            **이벤트명**: `status-update`

            **페이로드 형식**:
            ```json
            { "status": "PROCESSING", "step": "AUDIO_ANALYSIS" }
            { "status": "PROCESSING", "step": "FEEDBACK_GENERATION" }
            { "status": "COMPLETED" }
            { "status": "FAILED" }
            { "status": "EXPIRED" }
            ```

            | status | step | 설명 |
            |--------|------|------|
            | PROCESSING | AUDIO_ANALYSIS | STT 변환 중 |
            | PROCESSING | FEEDBACK_GENERATION | AI 피드백 생성 중 |
            | COMPLETED | (없음) | 분석 완료. 결과 조회 가능 |
            | FAILED | (없음) | 분석 실패 |
            | EXPIRED | (없음) | 타임아웃 (80초 초과) |

            **연결 종료 시점**: `COMPLETED`, `FAILED`, `EXPIRED` 이벤트 수신 시 서버에서 연결을 닫습니다.

            **Race Condition 방어**: 구독 시점에 이미 완료된 경우 즉시 이벤트를 전송하고 연결을 종료합니다.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "SSE 스트림 연결 성공. `status-update` 이벤트를 수신합니다.",
            content = @Content(
                mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                examples = {
                    @ExampleObject(name = "PROCESSING (STT 중)", value =
                        "event: status-update\ndata: {\"status\":\"PROCESSING\",\"step\":\"AUDIO_ANALYSIS\"}\n\n"),
                    @ExampleObject(name = "PROCESSING (피드백 생성 중)", value =
                        "event: status-update\ndata: {\"status\":\"PROCESSING\",\"step\":\"FEEDBACK_GENERATION\"}\n\n"),
                    @ExampleObject(name = "COMPLETED", value =
                        "event: status-update\ndata: {\"status\":\"COMPLETED\"}\n\n"),
                    @ExampleObject(name = "FAILED", value =
                        "event: status-update\ndata: {\"status\":\"FAILED\"}\n\n"),
                    @ExampleObject(name = "EXPIRED", value =
                        "event: status-update\ndata: {\"status\":\"EXPIRED\"}\n\n")
                }
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "토큰이 유효하지 않거나 만료됨 - INVALID_TOKEN",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "토큰의 attemptId와 경로의 attemptId 불일치 - FORBIDDEN",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "시도를 찾을 수 없음 - ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
        @Parameter(description = "카드 ID") @PathVariable Long cardId,
        @Parameter(description = "시도 ID") @PathVariable Long attemptId,
        @Parameter(description = "stream-token API에서 발급받은 1회용 토큰") @RequestParam String token
    ) {
        Map<String, Object> payload = sseTokenService.validateAndConsume(token);
        if (payload == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 토큰에 담긴 attemptId와 path variable 일치 여부 검증
        Long tokenAttemptId = ((Number) payload.get("attemptId")).longValue();
        if (!tokenAttemptId.equals(attemptId)) {
            log.warn("[SSE] 토큰 attemptId 불일치: tokenAttemptId={}, pathAttemptId={}", tokenAttemptId, attemptId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        log.info("[SSE] 스트림 구독 시작: attemptId={}", attemptId);
        return sseService.subscribe(attemptId);
    }
}