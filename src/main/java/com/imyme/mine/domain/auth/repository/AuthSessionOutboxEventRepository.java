package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.AuthSessionOutboxEvent;
import com.imyme.mine.domain.auth.entity.AuthSessionOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuthSessionOutboxEventRepository extends JpaRepository<AuthSessionOutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT e FROM AuthSessionOutboxEvent e
        WHERE e.status IN :statuses
          AND e.retryCount < :maxRetries
          AND e.nextRetryAt <= :now
        ORDER BY e.createdAt ASC
        """)
    List<AuthSessionOutboxEvent> findProcessableEvents(
        @Param("statuses") Collection<AuthSessionOutboxStatus> statuses,
        @Param("maxRetries") int maxRetries,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
}
