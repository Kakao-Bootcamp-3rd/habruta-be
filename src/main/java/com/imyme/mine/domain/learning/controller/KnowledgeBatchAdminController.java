package com.imyme.mine.domain.learning.controller;

import com.imyme.mine.domain.learning.dto.KnowledgeBatchResult;
import com.imyme.mine.domain.learning.service.KnowledgeBatchService;
import com.imyme.mine.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Knowledge 배치 관리자 컨트롤러
 * - 배치 작업 수동 트리거
 * - 테스트 및 디버깅 용도
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/knowledge/batch")
@RequiredArgsConstructor
@Tag(name = "12. Admin", description = "Knowledge 배치 관리 API")
public class KnowledgeBatchAdminController {

    private final KnowledgeBatchService batchService;

    /**
     * 전체 Knowledge 배치 즉시 실행
     * - 모든 활성 키워드에 대해 배치 실행
     * - 테스트 및 수동 실행 용도
     */
    @PostMapping("/trigger/now")
    @Operation(summary = "전체 배치 즉시 실행", description = "모든 활성 키워드에 대해 Knowledge 배치를 즉시 실행합니다.")
    public ApiResponse<KnowledgeBatchResult> triggerBatchNow() {
        log.info("=== Knowledge 배치 수동 실행 요청 ===");

        KnowledgeBatchResult result = batchService.executeDaily();

        log.info("=== Knowledge 배치 수동 실행 완료 ===");
        log.info("총 키워드: {}, 성공: {}, 실패: {}, 총 피드백: {}, 생성: {}, 업데이트: {}, 무시: {}",
            result.totalKeywords(),
            result.successKeywords(),
            result.failedKeywords(),
            result.totalFeedbacks(),
            result.createdKnowledge(),
            result.updatedKnowledge(),
            result.ignoredCandidates());

        return ApiResponse.success(result);
    }

    /**
     * 특정 키워드에 대한 배치 실행
     * - 단일 키워드 테스트 용도
     */
    @PostMapping("/trigger/keyword/{keywordId}")
    @Operation(summary = "특정 키워드 배치 실행", description = "특정 키워드에 대해서만 Knowledge 배치를 실행합니다.")
    public ApiResponse<KnowledgeBatchResult> triggerBatchForKeyword(
        @Parameter(description = "키워드 ID", required = true)
        @PathVariable Long keywordId
    ) {
        log.info("=== Knowledge 배치 수동 실행 - 키워드 ID: {} ===", keywordId);

        KnowledgeBatchResult result = batchService.processKeyword(keywordId);

        log.info("=== Knowledge 배치 완료 - 키워드 ID: {} ===", keywordId);
        log.info("총 피드백: {}, 생성: {}, 업데이트: {}, 무시: {}",
            result.totalFeedbacks(),
            result.createdKnowledge(),
            result.updatedKnowledge(),
            result.ignoredCandidates());

        return ApiResponse.success(result);
    }

    /**
     * 특정 키워드의 특정 기간 피드백에 대한 배치 실행
     * - 기간 지정 테스트 용도
     */
    @PostMapping("/trigger/keyword/{keywordId}/range")
    @Operation(summary = "특정 키워드의 기간별 배치 실행", description = "특정 키워드의 특정 기간 피드백에 대해 Knowledge 배치를 실행합니다.")
    public ApiResponse<KnowledgeBatchResult> triggerBatchForKeywordByDateRange(
        @Parameter(description = "키워드 ID", required = true)
        @PathVariable Long keywordId,

        @Parameter(description = "시작 일시 (ISO 8601 형식)", example = "2024-01-01T00:00:00")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,

        @Parameter(description = "종료 일시 (ISO 8601 형식)", example = "2024-01-31T23:59:59")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        log.info("=== Knowledge 배치 수동 실행 - 키워드 ID: {}, 기간: {} ~ {} ===",
            keywordId, startDate, endDate);

        KnowledgeBatchResult result = batchService.processKeywordByDateRange(
            keywordId, startDate, endDate
        );

        log.info("=== Knowledge 배치 완료 - 키워드 ID: {} ===", keywordId);

        return ApiResponse.success(result);
    }
}