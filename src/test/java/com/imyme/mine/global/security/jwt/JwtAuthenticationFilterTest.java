package com.imyme.mine.global.security.jwt;

import com.imyme.mine.domain.auth.entity.RoleType;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.service.AuthSessionCacheService;
import com.imyme.mine.global.security.UserPrincipal;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("JwtAuthenticationFilter 단위 테스트")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock AuthSessionCacheService authSessionCacheService;
    @Mock Tracer tracer;
    @Mock Span span;
    @Mock Tracer.SpanInScope spanInScope;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, authSessionCacheService, tracer);
        SecurityContextHolder.clearContext();

        lenient().when(tracer.nextSpan()).thenReturn(span);
        lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);
        lenient().when(span.name(anyString())).thenReturn(span);
        lenient().when(span.tag(anyString(), anyString())).thenReturn(span);
        lenient().when(span.start()).thenReturn(span);

        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getRequestURI()).thenReturn("/cards");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("role claim이 있으면 DB 사용자 조회 없이 UserPrincipal을 생성한다")
    void doFilterInternal_usesJwtClaimsWithoutUserLookupWhenRoleClaimExists() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("access-token")).thenReturn(1L);
        when(jwtTokenProvider.getRoleFromToken("access-token")).thenReturn("USER");
        when(jwtTokenProvider.getDeviceUuidFromToken("access-token")).thenReturn("device-1");
        when(authSessionCacheService.hasActiveSession(1L, "device-1")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(userRepository, never()).findById(1L);
        verify(authSessionCacheService).hasActiveSession(1L, "device-1");
        verify(authSessionCacheService, never()).hasActiveSession(1L);
        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("role claim이 없으면 기존 토큰 호환을 위해 DB 사용자 조회로 fallback한다")
    void doFilterInternal_fallsBackToUserLookupWhenRoleClaimMissing() throws Exception {
        User user = mock(User.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer legacy-token");
        when(jwtTokenProvider.validateToken("legacy-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("legacy-token")).thenReturn(1L);
        when(jwtTokenProvider.getRoleFromToken("legacy-token")).thenReturn(null);
        when(jwtTokenProvider.getDeviceUuidFromToken("legacy-token")).thenReturn(null);
        when(authSessionCacheService.hasActiveSession(1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getNickname()).thenReturn("tester");
        when(user.getRole()).thenReturn(RoleType.USER);

        filter.doFilterInternal(request, response, filterChain);

        verify(userRepository).findById(1L);
        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getNickname()).isEqualTo("tester");
    }
}
