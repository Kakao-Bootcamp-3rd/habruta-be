package com.imyme.mine.domain.challenge.controller;

import com.imyme.mine.domain.challenge.dto.request.CompleteUploadRequest;
import com.imyme.mine.domain.challenge.dto.request.CreateAttemptRequest;
import com.imyme.mine.domain.challenge.dto.response.ChallengeHistoryResponse;
import com.imyme.mine.domain.challenge.dto.response.ChallengeRankingResponse;
import com.imyme.mine.domain.challenge.dto.response.CreateAttemptResponse;
import com.imyme.mine.domain.challenge.dto.response.MyResultResponse;
import com.imyme.mine.domain.challenge.dto.response.TodayChallengeResponse;
import com.imyme.mine.domain.challenge.dto.response.UploadCompleteResponse;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.service.ChallengeAttemptService;
import com.imyme.mine.domain.challenge.service.ChallengeQueryService;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "11. Challenge", description = "챌린지 API")
@RestController
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeQueryService challengeQueryService;
    private final ChallengeAttemptService challengeAttemptService;

    // ===== Read API =====

    @Operation(
            summary = "오늘의 챌린지 조회",
            description = "오늘 날짜의 챌린지 정보와 내 참여 상태를 조회합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "오늘의 챌린지 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "오늘 챌린지 없음 - CHALLENGE_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping("/today")
    public ApiResponse<TodayChallengeResponse> getTodayChallenge(
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getTodayChallenge(principal.getId()));
    }

    @Operation(
            summary = "최근 챌린지 랭킹 조회",
            description = "가장 최근에 완료된 챌린지의 랭킹을 조회합니다. challengeId 없이 최신 결과를 바로 확인할 때 사용합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "랭킹 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "완료된 챌린지 없음 - CHALLENGE_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping("/latest-rankings")
    public ApiResponse<ChallengeRankingResponse> getLatestRankings(
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getLatestRankings(principal.getId(), page, size));
    }

    @Operation(
            summary = "챌린지 랭킹 조회",
            description = "완료된 챌린지의 랭킹을 페이지네이션으로 조회합니다. COMPLETED 상태가 아니면 조회 불가.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "랭킹 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "결과 미발표 - CHALLENGE_NOT_COMPLETED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "챌린지 없음 - CHALLENGE_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping("/{challengeId}/rankings")
    public ApiResponse<ChallengeRankingResponse> getRankings(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId,
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getRankings(challengeId, principal.getId(), page, size));
    }

    @Operation(
            summary = "챌린지 히스토리 조회",
            description = "챌린지 목록을 커서 기반 페이지네이션으로 조회합니다. status 필터, 참여한 챌린지만 필터 가능.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "히스토리 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 커서 - INVALID_CURSOR",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping
    public ApiResponse<ChallengeHistoryResponse> getChallengeHistory(
            @Parameter(description = "챌린지 상태 필터 (SCHEDULED, OPEN, CLOSED, ANALYZING, COMPLETED)")
            @RequestParam(required = false) ChallengeStatus status,
            @Parameter(description = "참여한 챌린지만 조회 여부")
            @RequestParam(defaultValue = "false") boolean participated,
            @Parameter(description = "페이지네이션 커서 (이전 응답의 nextCursor 값)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (최대 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeQueryService.getChallengeHistory(
                principal.getId(), cursor, size, status, participated));
    }

    // ===== Write API =====

    @Operation(
            summary = "챌린지 참여 시작",
            description = "챌린지에 참여를 시작하고 S3 업로드용 Presigned URL을 발급합니다. "
                    + "요청 바디에 contentType만 포함합니다 (fileSize는 녹음 전 알 수 없으므로 upload-complete 시 검증). "
                    + "PENDING 상태의 기존 참여가 있으면 새 URL을 재발급합니다 (200). "
                    + "신규 참여면 201을 반환합니다. "
                    + "클라이언트는 S3 PUT 업로드 시 createAttempt에 보낸 것과 동일한 Content-Type 헤더를 포함해야 합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신규 참여 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 PENDING 참여 재사용 (새 업로드 URL 발급)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "챌린지 미시작(CHALLENGE_NOT_STARTED) / 허용되지 않는 파일 형식(INVALID_CONTENT_TYPE)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 참여 완료 - ALREADY_PARTICIPATED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "종료된 챌린지 - CHALLENGE_ENDED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping("/{challengeId}/attempts")
    public ResponseEntity<ApiResponse<CreateAttemptResponse>> createAttempt(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId,
            @Valid @RequestBody CreateAttemptRequest request,
            @CurrentUser UserPrincipal principal
    ) {
        Map.Entry<CreateAttemptResponse, Boolean> result =
                challengeAttemptService.createAttempt(challengeId, principal.getId(), request);

        HttpStatus httpStatus = result.getValue() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(result.getKey()));
    }

    @Operation(
            summary = "업로드 완료 확정",
            description = "S3 업로드 완료 후 호출하여 참여를 확정합니다 (PENDING → UPLOADED). "
                    + "OPEN, CLOSED 상태에서 허용되며 ANALYZING 이후에는 불가합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업로드 완료 확정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "S3 업로드 미완료(UPLOAD_NOT_COMPLETED) / 챌린지 미시작(CHALLENGE_NOT_STARTED) / "
                            + "잘못된 Object Key(INVALID_OBJECT_KEY) / "
                            + "허용되지 않은 파일 형식(INVALID_CONTENT_TYPE) / 파일 크기 초과(FILE_TOO_LARGE)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "참여 기록 없음 - PARTICIPATION_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 제출 완료 - ALREADY_SUBMITTED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "종료된 챌린지 - CHALLENGE_ENDED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping("/{challengeId}/attempts/{attemptId}/upload-complete")
    public ApiResponse<UploadCompleteResponse> completeUpload(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId,
            @Parameter(description = "참여 ID", required = true) @PathVariable Long attemptId,
            @Valid @RequestBody CompleteUploadRequest request,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(
                challengeAttemptService.completeUpload(challengeId, attemptId, principal.getId(), request));
    }

    @Operation(
            summary = "내 챌린지 결과 조회",
            description = "내 챌린지 참여 결과를 조회합니다. "
                    + "COMPLETED가 아니면 분석 중 메시지를 반환하고, COMPLETED면 점수/순위/피드백을 반환합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "결과 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "참여 기록 없음(PARTICIPATION_NOT_FOUND) / 챌린지 없음(CHALLENGE_NOT_FOUND)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping("/{challengeId}/my-result")
    public ApiResponse<MyResultResponse> getMyResult(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId,
            @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(challengeAttemptService.getMyResult(challengeId, principal.getId()));
    }
}