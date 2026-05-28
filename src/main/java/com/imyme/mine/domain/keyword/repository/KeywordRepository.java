package com.imyme.mine.domain.keyword.repository;

import com.imyme.mine.domain.keyword.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    List<Keyword> findAllByCategoryIdAndIsActiveOrderByDisplayOrderAsc(Long categoryId, Boolean isActive);

    List<Keyword> findAllByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    @Query("SELECT k FROM Keyword k JOIN FETCH k.category WHERE k.isActive = :isActive ORDER BY k.category.displayOrder, k.displayOrder")
    List<Keyword> findAllWithCategoryByIsActive(@Param("isActive") Boolean isActive);

    @Query("SELECT k FROM Keyword k JOIN FETCH k.category ORDER BY k.category.displayOrder, k.displayOrder")
    List<Keyword> findAllWithCategory();

    @Query("SELECT k FROM Keyword k JOIN FETCH k.category WHERE k.id = :id AND k.category.id = :categoryId")
    Optional<Keyword> findByIdAndCategoryIdWithCategory(@Param("id") Long id, @Param("categoryId") Long categoryId);

    boolean existsByIdAndCategoryId(Long id, Long categoryId);
}
