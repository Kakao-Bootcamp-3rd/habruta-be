package com.imyme.mine.domain.challenge.service;

import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.sse.ChallengeParticipantSseRegistry;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 챌린지 참여자 수 SSE 서비스
 *
 * <p>OPEN 상태의 챌린지에 대해 실시간 참여자 수를 구독자에게 push.
 * upload-complete 커밋 직후 {@link #broadcast}를 호출해 count 갱신.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeParticipantSseService {

    private final ChallengeParticipantSseRegistry registry;
    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;

    /**
     * SSE 구독 — 즉시 현재 count를 초기 이벤트로 전송
     *
     * @param challengeId 구독할 챌린지 ID
     * @return SseEmitter (컨트롤러가 반환)
     */
    public SseEmitter subscribe(Long challengeId) {
        log.info("[Challenge SSE] 구독 요청: challengeId={}", challengeId);
        challengeRepository.findById(challengeId)
                .filter(c -> c.getStatus() == ChallengeStatus.OPEN)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        SseEmitter emitter = registry.subscribe(challengeId);

        // 초기 count 즉시 전송
        try {
            int count = challengeAttemptRepository.countSubmittedByChallengeId(challengeId);
            emitter.send(SseEmitter.event().name("count").data(Map.of("count", count)));
            log.debug("[Challenge SSE] 초기 count 전송: challengeId={}, count={}", challengeId, count);
        } catch (IOException e) {
            log.warn("[Challenge SSE] 초기 count 전송 실패: challengeId={}", challengeId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * upload-complete 커밋 후 호출 — 전체 구독자에게 최신 count push
     *
     * @param challengeId 대상 챌린지 ID
     */
    public void broadcast(Long challengeId) {
        int count = challengeAttemptRepository.countSubmittedByChallengeId(challengeId);
        registry.broadcast(challengeId, count);
    }

    /**
     * 챌린지 게이트 종료 시 호출 — 전체 emitter 정리
     *
     * @param challengeId 종료할 챌린지 ID
     */
    public void closeAll(Long challengeId) {
        registry.closeAll(challengeId);
    }
}
