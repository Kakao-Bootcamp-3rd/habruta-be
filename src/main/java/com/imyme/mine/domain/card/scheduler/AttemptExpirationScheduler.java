package com.imyme.mine.domain.card.scheduler;

import com.imyme.mine.domain.card.entity.AttemptStatus;
import com.imyme.mine.domain.card.entity.CardAttempt;
import com.imyme.mine.domain.card.repository.CardAttemptRepository;
import com.imyme.mine.global.config.AttemptProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 학습 시도 만료 처리 스케줄러
 * - PENDING 상태에서 10분 초과 시 EXPIRED로 자동 전환
 * - 1분마다 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttemptExpirationScheduler {

    private final CardAttemptRepository cardAttemptRepository;
    private final AttemptProperties attemptProperties;

    /**
     * PENDING 상태 만료 처리
     * - 생성 후 10분 초과한 PENDING 시도를 EXPIRED로 전환
     * - 1분마다 실행
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingAttempts() {
        LocalDateTime expirationThreshold = LocalDateTime.now().minus(Duration.ofMinutes(attemptProperties.getUploadExpirationMinutes()));

        List<CardAttempt> expiredAttempts = cardAttemptRepository
            .findByStatusAndCreatedAtBefore(AttemptStatus.PENDING, expirationThreshold);

        if (!expiredAttempts.isEmpty()) {
            for (CardAttempt attempt : expiredAttempts) {
                attempt.expire();
                log.info("학습 시도 만료 처리 - attemptId: {}, createdAt: {}",
                    attempt.getId(), attempt.getCreatedAt());
            }
            log.info("총 {}개의 PENDING 시도 만료 처리 완료", expiredAttempts.size());
        }
    }
}
