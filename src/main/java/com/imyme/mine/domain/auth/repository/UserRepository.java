package com.imyme.mine.domain.auth.repository;

import com.imyme.mine.domain.auth.entity.OAuthProviderType;
import com.imyme.mine.domain.auth.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // OAuth ID로 회원 조회
    Optional<User> findByOauthId(String oauthId);

    // OAuth ID와 Provider로 회원 조회 (E2E 테스트용)
    Optional<User> findByOauthIdAndOauthProvider(String oauthId, OAuthProviderType oauthProvider);

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 모든 닉네임 조회 (Redis 캐시 초기화용)
    @Query("SELECT u.nickname FROM User u WHERE u.deletedAt IS NULL")
    List<String> findAllNicknames();

    /** 챌린지 OPEN 알림 브로드캐스트용 전체 활성 유저 ID 페이지 조회 */
    @Query(value = """
        SELECT id FROM users
        WHERE deleted_at IS NULL
        ORDER BY id
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Long> findAllActiveIdsPaged(@Param("offset") int offset, @Param("limit") int limit);

    // -------------------------------------------------------------------------
    // 배치용 — @SQLRestriction("deleted_at IS NULL") 우회를 위해 네이티브 쿼리 사용
    // -------------------------------------------------------------------------

    /** 탈퇴 회원 Hard Delete 대상 조회 (30일 경과, 청크 단위) */
    @Query(value = """
        SELECT id, profile_image_key AS profileImageKey
        FROM users
        WHERE deleted_at IS NOT NULL
          AND deleted_at < :threshold
        LIMIT :limit
        """, nativeQuery = true)
    List<DeletedUserProjection> findDeletedUsersForHardDelete(
        @Param("threshold") LocalDateTime threshold,
        @Param("limit") int limit
    );

    /** 탈퇴 회원 DB Hard Delete */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM users WHERE id IN :ids", nativeQuery = true)
    int hardDeleteByIds(@Param("ids") List<Long> ids);

    /** 탈퇴 회원 S3 키 조회용 Projection */
    interface DeletedUserProjection {
        Long getId();
        String getProfileImageKey();
    }
}
