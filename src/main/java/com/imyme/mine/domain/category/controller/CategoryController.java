package com.imyme.mine.domain.category.controller;

import com.imyme.mine.domain.category.dto.CategoryResponse;
import com.imyme.mine.domain.category.service.CategoryService;
import com.imyme.mine.domain.keyword.dto.CategoryKeywordsResponse;
import com.imyme.mine.domain.keyword.service.KeywordService;
import com.imyme.mine.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "05. Category", description = "카테고리 조회 및 카테고리별 키워드 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final KeywordService keywordService;

    @Operation(
        summary = "카테고리 목록 조회",
        description = "전체 카테고리 목록을 조회합니다. isActive 필터로 활성/비활성 카테고리를 필터링할 수 있습니다."
    )
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories(
            @Parameter(description = "활성 상태 필터 (true: 활성만, false: 비활성만, null: 전체)") @RequestParam(required = false) Boolean isActive
    ) {
        List<CategoryResponse> categories = categoryService.getCategories(isActive);
        return ApiResponse.success(categories);
    }

    @Operation(
        summary = "카테고리별 키워드 목록 조회",
        description = "특정 카테고리에 속한 키워드 목록을 조회합니다."
    )
    @GetMapping("/{categoryId}/keywords")
    public ApiResponse<CategoryKeywordsResponse> getKeywordsByCategory(
            @Parameter(description = "카테고리 ID", required = true) @PathVariable Long categoryId,
            @Parameter(description = "활성 상태 필터 (true: 활성만, false: 비활성만, null: 전체)") @RequestParam(required = false) Boolean isActive
    ) {
        CategoryKeywordsResponse response = keywordService.getKeywordsByCategory(categoryId, isActive);
        return ApiResponse.success(response);
    }
}
