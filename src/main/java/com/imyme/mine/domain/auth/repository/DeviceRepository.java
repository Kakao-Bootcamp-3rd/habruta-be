package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Device 레포지토리
 * - 기기 정보 관리 및 Soft Delete 지원
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // deviceUuid로 기기 조회 (Soft Delete 필터 적용)
    Optional<Device> findByDeviceUuid(String deviceUuid);

    // FCM 푸시 발송 가능한 기기 조회 (fcmToken 있음 + 푸시 수신 동의)
    @Query("SELECT d FROM Device d WHERE d.lastUser.id = :userId AND d.fcmToken IS NOT NULL AND d.isPushEnabled = true")
    List<Device> findPushableDevicesByUserId(@Param("userId") Long userId);

    // deviceUuid로 기기 Soft Delete
    @Modifying
    @Query("UPDATE Device d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.deviceUuid = :deviceUuid AND d.deletedAt IS NULL")
    void softDeleteByDeviceUuid(@Param("deviceUuid") String deviceUuid);

    // 회원 탈퇴용 : 기기를 삭제하는 게 아니라 '주인 없음' 상태로 만듦
    @Modifying
    @Query("UPDATE Device d SET d.lastUser = null WHERE d.lastUser.id = :userId")
    void unlinkAllByUserId(@Param("userId") Long userId);

    // 미사용 기기 Soft Delete (배치용: 6개월 이상 미활성 기기)
    @Modifying
    @Transactional
    @Query("UPDATE Device d SET d.deletedAt = CURRENT_TIMESTAMP WHERE d.lastActiveAt < :threshold AND d.deletedAt IS NULL")
    int softDeleteInactiveDevices(@Param("threshold") LocalDateTime threshold);
}
