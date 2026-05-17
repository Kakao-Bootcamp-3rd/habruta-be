package com.imyme.mine.domain.auth.scheduler;

import com.imyme.mine.domain.auth.service.AuthSessionOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionOutboxScheduler {

    private final AuthSessionOutboxService authSessionOutboxService;

    @Scheduled(fixedDelay = 10000)
    public void processAuthSessionOutbox() {
        int processed = authSessionOutboxService.processDueEvents();
        if (processed > 0) {
            log.info("[AuthSessionOutbox] Redis 세션 동기화 이벤트 처리 완료 - {}건", processed);
        }
    }
}
