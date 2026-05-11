package com.imyme.mine.global.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final HealthController healthController = new HealthController(jdbcTemplate);

    @Test
    void healthReturnsOkWhenDatabaseAndSessionQueryAreReady() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Boolean.class), eq(-1L))).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = healthController.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("database", "UP");
        assertThat(response.getBody()).containsEntry("sessionQuery", "UP");
        verify(jdbcTemplate).queryForObject("select 1", Integer.class);
        verify(jdbcTemplate).queryForObject(any(String.class), eq(Boolean.class), eq(-1L));
    }

    @Test
    void healthReturnsServiceUnavailableWhenDatabaseCheckFails() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class))
                .thenThrow(new IllegalStateException("db is not ready"));

        ResponseEntity<Map<String, Object>> response = healthController.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        assertThat(response.getBody()).containsEntry("database", "DOWN");
    }
}
