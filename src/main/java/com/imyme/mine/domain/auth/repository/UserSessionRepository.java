package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // Refresh Token으로 세션 조회
    Optional<UserSession> findByRefreshToken(String refreshToken);

    // Refresh Token으로 세션 + user + device 한 번에 조회
    @Query("""
        SELECT s FROM UserSession s
        JOIN FETCH s.user
        JOIN FETCH s.device
        WHERE s.refreshToken = :hashedToken
        """)
    Optional<UserSession> findByRefreshTokenWithRelations(@Param("hashedToken") String hashedToken);

    // 사용자의 모든 세션 삭제 (로그아웃 전체)
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    // 특정 사용자의 특정 기기 세션 삭제 (device_uuid 기반 로그아웃)
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user.id = :userId AND s.device.deviceUuid = :deviceUuid")
    void deleteByUserIdAndDeviceUuid(@Param("userId") Long userId, @Param("deviceUuid") String deviceUuid);

    // device_uuid로 세션 조회
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.device.deviceUuid = :deviceUuid")
    Optional<UserSession> findByUserIdAndDeviceUuid(@Param("userId") Long userId, @Param("deviceUuid") String deviceUuid);

    // deviceId로 모든 세션 삭제 (기기 삭제 시)
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.device.id = :deviceId")
    void deleteAllByDeviceId(@Param("deviceId") Long deviceId);

    // 사용자의 활성 세션 존재 여부 확인 (로그아웃 여부 체크용)
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSession s WHERE s.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);

    // 만료된 세션 일괄 삭제 (배치용)
    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);
}
