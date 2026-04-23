package com.imyme.mine.global.config;

import com.imyme.mine.global.security.handler.JwtAccessDeniedHandler;
import com.imyme.mine.global.security.handler.JwtAuthenticationEntryPoint;
import com.imyme.mine.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 설정
 * - JWT 기반 인증 및 권한 관리
 * - CORS 설정
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 2. CORS 설정 연결 (아래 corsConfigurationSource 메서드 사용)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 세션 미사용
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. 예외 처리
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // 5. 권한 설정 (중복 경로 제거 & 와일드카드 활용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/health",        // Nginx가 /server 떼고 줌
                                "/server/health", // Nginx 설정 꼬였을 때를 대비해 비상용으로만 남김
                                "/categories/**",
                                "/keywords/**",
                                "/auth/**",
                                "/oauth2/**",     // 혹시 모를 OAuth 기본 경로
                                "/e2e/**", // E2E 테스트 전용 (test 프로파일에서만 활성화)
                                "/admin/challenge/**", // 챌린지 수동 트리거 (dev/release 프로파일에서만 활성화)
                                "/ws/**",  // WebSocket 엔드포인트 (핸드셰이크에서 JWT 검증)
                                "/websocket-test", // WebSocket 테스트 페이지
                                "/test/pvp/**", // PvP WebSocket 테스트 API
                                "/test/sse/**", // SSE 수동 테스트 API
                                "/error",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/actuator/health",      // 헬스체크 (화이트리스트)
                                "/actuator/prometheus",  // Prometheus 메트릭 (화이트리스트)
                                "/cards/*/attempts/*/stream",          // SSE 스트림 (토큰 인증, JWT 미사용)
                                "/challenges/*/participants/stream"    // 챌린지 참여자 수 SSE (인증 불필요)
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())

                // 6. 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 🔥 CORS 설정 - .env 파일의 CORS_ALLOWED_ORIGINS 환경변수로 관리
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. 허용할 출처
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());

        // 2. 허용할 메소드
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 3. 허용할 헤더
        configuration.setAllowedHeaders(List.of("*"));

        // 4. 자격 증명 허용 (쿠키, Authorization 헤더 등)
        configuration.setAllowCredentials(true);

        // 5. 브라우저가 Authorization 헤더를 읽을 수 있게 노출
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));

        configuration.setMaxAge(3600L); // 1시간 캐시

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
