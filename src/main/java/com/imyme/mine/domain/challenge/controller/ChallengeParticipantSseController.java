package com.imyme.mine.domain.challenge.controller;

import com.imyme.mine.domain.challenge.service.ChallengeParticipantSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 챌린지 참여자 수 실시간 SSE 컨트롤러
 *
 * <p>인증 없이 접근 가능 (SecurityConfig permitAll).
 * 챌린지 OPEN 상태에서만 구독 가능하며, 게이트 종료(CLOSED) 시 자동으로 스트림이 닫힘.
 */
@Tag(name = "Challenge", description = "챌린지 API")
@RestController
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeParticipantSseController {

    private final ChallengeParticipantSseService sseService;

    /**
     * 챌린지 참여자 수 SSE 구독
     *
     * <p>연결 즉시 현재 count를 {@code count} 이벤트로 전송.
     * 이후 upload-complete 발생 시마다 갱신된 count push.
     * 챌린지 종료(CLOSED) 시 스트림 자동 종료.
     *
     * <pre>
     * event: count
     * data: {"count": 5}
     * </pre>
     */
    @Operation(summary = "챌린지 참여자 수 실시간 구독 (SSE)")
    @GetMapping(value = "/{challengeId}/participants/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long challengeId) {
        return sseService.subscribe(challengeId);
    }
}
