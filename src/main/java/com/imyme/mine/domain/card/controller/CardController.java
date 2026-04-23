package com.imyme.mine.domain.card.controller;

import com.imyme.mine.domain.card.dto.CardCreateRequest;
import com.imyme.mine.domain.card.dto.CardDetailResponse;
import com.imyme.mine.domain.card.dto.CardListResponse;
import com.imyme.mine.domain.card.dto.CardResponse;
import com.imyme.mine.domain.card.dto.CardUpdateRequest;
import com.imyme.mine.domain.card.dto.CardUpdateResponse;
import com.imyme.mine.domain.card.service.CardService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "07. Card", description = "학습 카드 생성/조회/수정/삭제 API")
@Slf4j
@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @Operation(
        summary = "학습 카드 생성",
        description = "새로운 학습 카드를 생성합니다. 텍스트와 음성 녹음을 포함한 카드를 만들 수 있습니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "카드 생성 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Validation 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CardResponse> createCard(
        @CurrentUser UserPrincipal userPrincipal,
        @Valid @RequestBody CardCreateRequest request
    ) {
        Long userId = userPrincipal.getId();
        log.info("POST /cards - userId: {}", userId);

        CardResponse response = cardService.createCard(userId, request);

        return ApiResponse.success(response, "카드가 생성되었습니다.");
    }

    @Operation(
        summary = "학습 카드 제목 수정",
        description = "기존 학습 카드의 제목을 수정합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "카드 제목 수정 성공"
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
            description = "카드를 찾을 수 없음 - CARD_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PatchMapping("/{cardId}")
    public ApiResponse<CardUpdateResponse> updateCardTitle(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "수정할 카드 ID", required = true) @PathVariable Long cardId,
        @Valid @RequestBody CardUpdateRequest request
    ) {
        Long userId = userPrincipal.getId();
        log.info("PATCH /cards/{} - userId: {}", cardId, userId);

        CardUpdateResponse response = cardService.updateCardTitle(userId, cardId, request);

        return ApiResponse.success(response, "카드 제목이 수정되었습니다.");
    }

    @Operation(
        summary = "학습 카드 삭제",
        description = "학습 카드를 삭제합니다. (Soft Delete)",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "카드 삭제 성공"
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
    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "삭제할 카드 ID", required = true) @PathVariable Long cardId
    ) {
        Long userId = userPrincipal.getId();
        log.info("DELETE /cards/{} - userId: {}", cardId, userId);

        cardService.deleteCard(userId, cardId);
    }

    @Operation(
        summary = "학습 카드 목록 조회",
        description = "사용자의 학습 카드 목록을 조회합니다. 카테고리, 키워드 필터링 및 커서 기반 페이지네이션을 지원합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "카드 목록 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @GetMapping
    public ApiResponse<CardListResponse> getCards(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "카테고리 ID 필터") @RequestParam(name = "category_id", required = false) Long categoryId,
        @Parameter(description = "키워드 ID 목록 필터") @RequestParam(name = "keyword_ids", required = false) List<Long> keywordIds,
        @Parameter(description = "고스트 카드 포함 여부 (기본값: true)") @RequestParam(name = "ghost", required = false, defaultValue = "true") Boolean ghost,
        @Parameter(description = "정렬 방식 (recent, oldest)") @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort,
        @Parameter(description = "커서 (페이지네이션용)") @RequestParam(name = "cursor", required = false) String cursor,
        @Parameter(description = "한 번에 가져올 카드 수 (1-100, 기본값: 20)")
        @RequestParam(name = "limit", required = false, defaultValue = "20")
        @Min(1) @Max(100) Integer limit
    ) {
        Long userId = userPrincipal.getId();
        log.info("GET /cards - userId: {}, categoryId: {}, keywordIds: {}, ghost: {}, sort: {}",
            userId, categoryId, keywordIds, ghost, sort);

        boolean excludeGhost = !Boolean.TRUE.equals(ghost);

        CardListResponse response = cardService.getCards(
            userId, categoryId, keywordIds, excludeGhost, sort, cursor, limit
        );

        return ApiResponse.success(response);
    }

    @Operation(
        summary = "학습 카드 상세 조회",
        description = "특정 학습 카드의 상세 정보를 조회합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "카드 상세 조회 성공"
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
    @GetMapping("/{cardId}")
    public ApiResponse<CardDetailResponse> getCardDetail(
        @CurrentUser UserPrincipal userPrincipal,
        @Parameter(description = "조회할 카드 ID", required = true) @PathVariable Long cardId
    ) {
        Long userId = userPrincipal.getId();
        log.info("GET /cards/{} - userId: {}", cardId, userId);

        CardDetailResponse response = cardService.getCardDetail(userId, cardId);

        return ApiResponse.success(response);
    }
}
