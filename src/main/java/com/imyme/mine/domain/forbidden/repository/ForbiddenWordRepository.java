package com.imyme.mine.domain.forbidden.repository;

import com.imyme.mine.domain.forbidden.entity.ForbiddenWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 금지어 레포지토리
 */
@Repository
public interface ForbiddenWordRepository extends JpaRepository<ForbiddenWord, Long> {
}
