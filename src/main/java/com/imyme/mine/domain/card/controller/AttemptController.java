package com.imyme.mine.domain.card.controller;

import com.imyme.mine.domain.card.dto.AttemptCreateRequest;
import com.imyme.mine.domain.card.dto.AttemptCreateResponse;
import com.imyme.mine.domain.card.dto.AttemptDetailResponse;
import com.imyme.mine.domain.card.dto.UploadCompleteRequest;
import com.imyme.mine.domain.card.dto.UploadCompleteResponse;
import com.imyme.mine.domain.card.service.AttemptService;
import jakarta.validation.Valid;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "08. Attempt", description = "학습 시도 생성/조회/삭제, 오디오 업로드 완료 처리 API")
@Slf4j
@RestController
@RequestMapping("/cards/{cardId}/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @Operation(
        summary = "학습 시도 생성",
        description = "새로운 학습 시도를 생성하고 S3 업로드용 Presigned URL을 발급합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "학습 시도 생성 성공 및 Presigned URL 발급"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "카드를 찾을 수 없음 - CARD_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttemptCreateResponse> createAttempt(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "학습 카드 ID", required = true) @PathVariable Long cardId,
        @RequestBody(required = false) AttemptCreateRequest request
    ) {
        Long userId = userPrincipal.getId();
        log.info("POST /cards/{}/attempts - userId: {}", cardId, userId);

        if (request == null) {
            request = new AttemptCreateRequest(null);
        }

        AttemptCreateResponse response = attemptService.createAttempt(userId, cardId, request);

        return ApiResponse.success(response);
    }

    @Operation(
        summary = "학습 시도 상세 조회",
        description = "특정 학습 시도의 상세 정보를 조회합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "학습 시도 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "학습 시도를 찾을 수 없음 - ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @GetMapping("/{attemptId}")
    public ApiResponse<AttemptDetailResponse> getAttemptDetail(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "학습 카드 ID", required = true) @PathVariable Long cardId,
        @Parameter(description = "학습 시도 ID", required = true) @PathVariable Long attemptId
    ) {
        Long userId = userPrincipal.getId();
        log.info("GET /cards/{}/attempts/{} - userId: {}", cardId, attemptId, userId);

        AttemptDetailResponse response = attemptService.getAttemptDetail(userId, cardId, attemptId);

        return ApiResponse.success(response);
    }

    @Operation(
        summary = "오디오 업로드 완료 처리",
        description = "S3 업로드 완료 후 호출하여 학습 시도를 확정합니다. 업로드한 오디오 파일의 S3 Key를 전달합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "업로드 완료 처리 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Validation 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "학습 시도를 찾을 수 없음 - ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PutMapping("/{attemptId}/upload-complete")
    public ApiResponse<UploadCompleteResponse> uploadComplete(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "학습 카드 ID", required = true) @PathVariable Long cardId,
        @Parameter(description = "학습 시도 ID", required = true) @PathVariable Long attemptId,
        @Valid @RequestBody UploadCompleteRequest request
    ) {
        Long userId = userPrincipal.getId();
        log.info("PUT /cards/{}/attempts/{}/upload-complete - userId: {}", cardId, attemptId, userId);

        UploadCompleteResponse response = attemptService.uploadComplete(userId, cardId, attemptId, request);

        return ApiResponse.success(response);
    }

    @Operation(
        summary = "학습 시도 삭제",
        description = "학습 시도를 삭제합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "학습 시도 삭제 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "학습 시도를 찾을 수 없음 - ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @DeleteMapping("/{attemptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttempt(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "학습 카드 ID", required = true) @PathVariable Long cardId,
        @Parameter(description = "학습 시도 ID", required = true) @PathVariable Long attemptId
    ) {
        Long userId = userPrincipal.getId();
        log.info("DELETE /cards/{}/attempts/{} - userId: {}", cardId, attemptId, userId);

        attemptService.deleteAttempt(userId, cardId, attemptId);
    }
}
