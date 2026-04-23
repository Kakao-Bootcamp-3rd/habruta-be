package com.imyme.mine.global.controller;

import java.time.LocalDateTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health Check API
 * - 서버 상태 확인용
 */
@Tag(name = "01. Health", description = "서버 상태 확인 API")
@RestController
public class HealthController {

    @Operation(
        summary = "서버 헬스체크",
        description = "서버의 정상 작동 여부를 확인합니다. 로드밸런서 또는 모니터링 도구에서 사용됩니다."
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now(),
                "service", "Mine Backend API"));
    }
}
