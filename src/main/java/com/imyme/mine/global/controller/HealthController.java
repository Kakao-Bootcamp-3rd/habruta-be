package com.imyme.mine.global.controller;

import java.time.LocalDateTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health Check API
 * - 서버 상태 확인용
 */
@Tag(name = "01. Health", description = "서버 상태 확인 API")
@RestController
public class HealthController {

    private static final String SESSION_EXISTS_WARMUP_SQL = """
            select case when count(us1_0.id)>0 then true else false end
            from user_sessions us1_0
            where us1_0.user_id=?
            """;

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(
        summary = "서버 헬스체크",
        description = "서버의 정상 작동 여부를 확인합니다. 로드밸런서 또는 모니터링 도구에서 사용됩니다."
    )
    @GetMapping({"/health", "/server/health"})
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            jdbcTemplate.queryForObject(SESSION_EXISTS_WARMUP_SQL, Boolean.class, -1L);

            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "timestamp", LocalDateTime.now(),
                    "service", "Mine Backend API",
                    "database", "UP",
                    "sessionQuery", "UP"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN",
                    "timestamp", LocalDateTime.now(),
                    "service", "Mine Backend API",
                    "database", "DOWN",
                    "reason", e.getClass().getSimpleName()));
        }
    }
}
