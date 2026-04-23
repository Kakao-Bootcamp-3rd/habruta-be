package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.dto.request.UpdateHistoryRequest;
import com.imyme.mine.domain.pvp.dto.response.MyRoomsResponse;
import com.imyme.mine.domain.pvp.dto.response.UpdateHistoryResponse;
import com.imyme.mine.domain.pvp.service.PvpRoomService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "11. PvP", description = "PvP 대결 방/기록 API")
@Slf4j
@RestController
@RequestMapping("/pvp/histories")
@RequiredArgsConstructor
public class PvpHistoryController {

    private final PvpRoomService pvpRoomService;

    /**
     * 4.8 내 PvP 기록 조회
     */
    @Operation(
            summary = "내 PvP 기록 조회",
            description = "내 PvP 대결 기록을 커서 페이징으로 조회합니다. 정렬 옵션(finishedAt, score)과 필터링(categoryId, keywordId, includeHidden)을 지원합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기록 조회 성공"
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
    public ApiResponse<MyRoomsResponse> getMyHistories(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "카테고리 ID 필터") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "키워드 ID 필터") @RequestParam(required = false) Long keywordId,
            @Parameter(description = "숨긴 기록 포함 여부") @RequestParam(defaultValue = "false") boolean includeHidden,
            @Parameter(description = "정렬 기준 (finishedAt, score)") @RequestParam(defaultValue = "finishedAt") String sort,
            @Parameter(description = "페이지네이션 커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("내 PvP 기록 조회: userId={}, categoryId={}, keywordId={}, sort={}",
                principal.getId(), categoryId, keywordId, sort);

        MyRoomsResponse response = pvpRoomService.getMyHistories(
                principal.getId(), categoryId, keywordId, includeHidden, sort, cursor, size);

        return ApiResponse.success(response);
    }

    /**
     * 4.9 방 숨기기
     */
    @Operation(
            summary = "방 숨기기",
            description = "PvP 대결 기록의 숨김 상태를 변경합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "숨김 상태 변경 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 - FORBIDDEN",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "기록 없음 - NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PatchMapping("/my-rooms/{historyId}")
    public ApiResponse<UpdateHistoryResponse> updateHistoryVisibility(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "기록 ID", required = true) @PathVariable Long historyId,
            @Valid @RequestBody UpdateHistoryRequest request) {

        log.info("기록 숨김 상태 변경: userId={}, historyId={}, isHidden={}",
                principal.getId(), historyId, request.isHidden());

        UpdateHistoryResponse response = pvpRoomService.updateHistoryVisibility(
                principal.getId(), historyId, request);

        return ApiResponse.success(response);
    }
}