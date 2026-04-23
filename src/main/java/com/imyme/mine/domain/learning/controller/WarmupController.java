package com.imyme.mine.domain.learning.controller;

import com.imyme.mine.domain.learning.dto.WarmupResponse;
import com.imyme.mine.domain.learning.service.WarmupService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09. Audio", description = "학습 오디오 업로드용 Presigned URL 발급 API")
@Slf4j
@RestController
@RequestMapping("/learning/warmup")
@RequiredArgsConstructor
public class WarmupController {

    private final WarmupService warmupService;

    @Operation(
        summary = "STT 서버 워밍업",
        description = "GPU 콜드 스타트 방지를 위한 워밍업 요청. " +
                      "비동기로 처리되며 즉시 응답합니다. " +
                      "쿨다운 기간(30초) 내 중복 호출 시 AI 서버 호출을 생략하여 불필요한 부하를 방지합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202",
            description = "워밍업 시작 (Accepted) - 쿨다운 기간 내면 AI 서버 호출 생략"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<WarmupResponse> warmup(@CurrentUser UserPrincipal userPrincipal) {
        Long userId = userPrincipal.getId();
        log.info("POST /learning/warmup - userId: {}", userId);

        WarmupResponse response = warmupService.warmup(userId);

        return ApiResponse.success(response);
    }
}
