package com.imyme.mine.domain.keyword.controller;

import com.imyme.mine.domain.keyword.dto.CategoryWithKeywordsResponse;
import com.imyme.mine.domain.keyword.service.KeywordService;
import com.imyme.mine.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "06. Keyword", description = "카테고리별 키워드 전체 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/keywords")
public class KeywordController {

    private final KeywordService keywordService;

    @Operation(
        summary = "전체 키워드 조회 (카테고리별 그룹화)",
        description = "모든 카테고리와 해당 카테고리에 속한 키워드를 조회합니다. isActive 필터로 활성/비활성 데이터를 필터링할 수 있습니다."
    )
    @GetMapping
    public ApiResponse<List<CategoryWithKeywordsResponse>> getAllKeywords(
            @Parameter(description = "활성 상태 필터 (true: 활성만, false: 비활성만, null: 전체)") @RequestParam(required = false) Boolean isActive
    ) {
        List<CategoryWithKeywordsResponse> keywords = keywordService.getAllKeywordsGroupedByCategory(isActive);
        return ApiResponse.success(keywords);
    }
}
