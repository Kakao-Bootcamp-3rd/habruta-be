package com.imyme.mine.domain.card.controller;

import com.imyme.mine.global.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * SSE 수동 테스트용 컨트롤러 (개발/테스트 환경 전용)
 * - AI 서버 없이 SSE 이벤트를 직접 주입하여 스트림 동작 검증
 */
@Slf4j
@RestController
@RequestMapping("/test/sse")
@RequiredArgsConstructor
public class SseTestController {

    private final SseEmitterRegistry sseEmitterRegistry;

    /**
     * SSE 테스트 페이지 서빙
     */
    @GetMapping(value = "/page", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String testPage() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/sse-test.html");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 중간 단계 이벤트 수동 push (연결 유지)
     * - AUDIO_ANALYSIS → FEEDBACK_GENERATION 전환 시뮬레이션
     */
    @PostMapping("/{attemptId}/push")
    public Map<String, Object> pushStep(
        @PathVariable Long attemptId,
        @RequestParam(defaultValue = "AUDIO_ANALYSIS") String step
    ) {
        sseEmitterRegistry.push(attemptId, Map.of("status", "PROCESSING", "step", step));
        log.info("[SSE 테스트] push: attemptId={}, step={}", attemptId, step);
        return Map.of("success", true, "attemptId", attemptId, "step", step);
    }

    /**
     * 종료 이벤트 수동 emit (연결 종료)
     * - COMPLETED / FAILED / EXPIRED 시뮬레이션
     */
    @PostMapping("/{attemptId}/emit")
    public Map<String, Object> emitStatus(
        @PathVariable Long attemptId,
        @RequestParam(defaultValue = "COMPLETED") String status
    ) {
        sseEmitterRegistry.emit(attemptId, status);
        log.info("[SSE 테스트] emit: attemptId={}, status={}", attemptId, status);
        return Map.of("success", true, "attemptId", attemptId, "status", status);
    }
}