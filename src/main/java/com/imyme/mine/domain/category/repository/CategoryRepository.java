package com.imyme.mine.domain.category.repository;

import com.imyme.mine.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByIsActiveOrderByDisplayOrderAsc(Boolean isActive);

    List<Category> findAllByOrderByDisplayOrderAsc();
}
