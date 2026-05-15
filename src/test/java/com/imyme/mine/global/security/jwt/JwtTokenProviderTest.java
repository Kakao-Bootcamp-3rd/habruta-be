package com.imyme.mine.global.security.jwt;

import com.imyme.mine.global.config.JwtProperties;
import com.imyme.mine.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("12345678901234567890123456789012");
        jwtProperties.setAccessTokenExpiration(900000L);
        jwtProperties.setRefreshTokenExpiration(604800000L);

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    @DisplayName("Access Token에 role claim을 포함한다")
    void generateAccessToken_includesRoleClaim() {
        String token = jwtTokenProvider.generateAccessToken(1L, "USER");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("기존 Access Token은 role claim 없이도 읽을 수 있다")
    void generateAccessToken_withoutRoleKeepsLegacyCompatibility() {
        String token = jwtTokenProvider.generateAccessToken(1L);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isNull();
    }

    @Test
    @DisplayName("JWT claim 값으로 UserPrincipal을 생성한다")
    void userPrincipal_fromClaimsBuildsAuthorities() {
        UserPrincipal principal = UserPrincipal.fromClaims(1L, "ADMIN");

        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getEmail()).isNull();
        assertThat(principal.getNickname()).isNull();
        assertThat(principal.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_ADMIN");
    }
}
