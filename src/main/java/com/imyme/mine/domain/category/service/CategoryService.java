package com.imyme.mine.domain.category.service;

import com.imyme.mine.domain.category.dto.CategoryResponse;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 카테고리 목록 조회 (캐싱 적용)
     * - TTL: 2시간 (RedisConfig에서 설정)
     * - sync=true: Cache Stampede 방지 (TTL 만료 시 동시 요청 폭주 방지)
     * - key: isActive 값별로 개별 캐싱 (null/true/false)
     */
    @Cacheable(value = "categories", key = "#isActive != null ? #isActive : 'all'", sync = true)
    public List<CategoryResponse> getCategories(Boolean isActive) {
        if (isActive == null) {
            return categoryRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(CategoryResponse::from)
                .toList();
        }
        return categoryRepository.findAllByIsActiveOrderByDisplayOrderAsc(isActive).stream()
            .map(CategoryResponse::from)
            .toList();
    }
}
