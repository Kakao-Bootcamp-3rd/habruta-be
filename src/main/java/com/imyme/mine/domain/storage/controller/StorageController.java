package com.imyme.mine.domain.storage.controller;

import com.imyme.mine.domain.storage.dto.PresignedUrlRequest;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.domain.storage.service.StorageService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "09. Audio", description = "학습 오디오 업로드용 Presigned URL 발급 API")
@Slf4j
@RestController
@RequestMapping("/learning")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @Operation(
        summary = "학습 오디오 Presigned URL 발급",
        description = "학습 오디오를 S3에 직접 업로드하기 위한 서명된 URL을 발급합니다. 시도 ID를 입력받아 해당 시도에 업로드합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Presigned URL 발급 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Validation 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "접근 권한 없음 - FORBIDDEN",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "시도를 찾을 수 없음 - ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "410",
            description = "업로드 제한 시간 초과 - UPLOAD_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/presigned-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PresignedUrlResponse> generatePresignedUrl(
        @CurrentUser UserPrincipal userPrincipal,
        @Valid @RequestBody PresignedUrlRequest request
    ) {
        Long userId = userPrincipal.getId();
        log.info("POST /learning/presigned-url - userId: {}, attemptId: {}", userId, request.attemptId());

        PresignedUrlResponse response = storageService.generatePresignedUrl(userId, request);

        return ApiResponse.success(response);
    }
}
