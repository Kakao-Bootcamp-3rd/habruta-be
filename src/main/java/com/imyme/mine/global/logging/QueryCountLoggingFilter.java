package com.imyme.mine.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청마다 SQL 쿼리 수 + 소요시간 로깅
 * [로컬 측정용] 커밋 금지
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class QueryCountLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        QueryCountInterceptor.reset();
        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            long queryCount = QueryCountInterceptor.getCount();

            if (queryCount > 0) {
                String warn = queryCount >= 10 ? " ⚠️ N+1?" : "";
                log.warn("[QueryCount] {} {} → {}ms, {}queries{}",
                        request.getMethod(), request.getRequestURI(),
                        elapsed, queryCount, warn);
            }

            QueryCountInterceptor.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs");
    }
}
