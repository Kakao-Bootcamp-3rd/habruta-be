package com.imyme.mine.domain.user.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.DeviceRepository;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.auth.repository.UserSessionRepository;
import com.imyme.mine.domain.forbidden.entity.ForbiddenWordType;
import com.imyme.mine.domain.forbidden.service.ForbiddenWordService;
import com.imyme.mine.domain.user.dto.NicknameCheckResponse;
import com.imyme.mine.domain.user.dto.NicknameCheckResponse.ReasonCode;
import com.imyme.mine.domain.user.dto.UpdateProfileRequest;
import com.imyme.mine.domain.user.dto.UserProfileResponse;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 관리 서비스
 * - 프로필 조회/수정, 닉네임 검증, 회원 탈퇴 등
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;
    private final ForbiddenWordService forbiddenWordService;
    private final ProfileImageService profileImageService;

    /**
     * 회원 탈퇴 처리
     * - User Soft Delete (deleted_at 설정)
     * - 관련 세션 Hard Delete
     * - 기기와 사용자 연결 해제 (lastUser = NULL)
     * - 캐시 무효화 (userProfile만 삭제, PvP 피드백은 유지)
     */
    @Transactional
    @CacheEvict(value = "userProfile", key = "#userId")
    public void withdrawUser(Long userId) {
        log.info("회원 탈퇴 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ALREADY_DELETED);
        }

        int deletedSessions = userSessionRepository.deleteAllByUserId(userId);
        log.info("삭제된 세션 수: {}", deletedSessions);

        deviceRepository.unlinkAllByUserId(userId);
        log.info("기기 연결 해제 완료");

        userRepository.delete(user);
        log.info("회원 탈퇴 완료: userId={}", userId);
    }

    /**
     * 프로필 조회 (캐싱 적용)
     * - TTL: 30분 (RedisConfig에서 설정)
     * - 프로필 수정 시 캐시 무효화
     */
    @Cacheable(value = "userProfile", key = "#userId")
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String profileImageUrl = profileImageService.resolveProfileImageUrl(user.getProfileImageKey(), user.getProfileImageUrl());
        return UserProfileResponse.from(user, profileImageUrl);
    }

    /**
     * 프로필 수정 (캐시 무효화)
     * - Dirty Checking 활용
     * - 수정 후 캐시 자동 삭제
     */
    @CacheEvict(value = "userProfile", key = "#userId")
    public void evictProfileCache(Long userId) {}

    @CacheEvict(value = "userProfile", key = "#userId")
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 닉네임 변경 (변경 사항이 있을 때만 검증 및 업데이트)
        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            validateNicknameForUpdate(request.nickname());
            user.updateNickname(request.nickname());
        }

        if (request.profileImageKey() != null) {
            user.updateProfileImage(null, request.profileImageKey());
        }

        String profileImageUrl = profileImageService.resolveProfileImageUrl(user.getProfileImageKey(), user.getProfileImageUrl());
        return UserProfileResponse.from(user, profileImageUrl);
    }

    // 닉네임 중복 확인
    @Transactional(readOnly = true)
    public NicknameCheckResponse checkNickname(String nickname, Long currentUserId) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return NicknameCheckResponse.ofUnavailable(ReasonCode.INVALID_LENGTH);
        }

        if (nickname.length() > 20) {
            return NicknameCheckResponse.ofUnavailable(ReasonCode.INVALID_LENGTH);
        }

        if (!nickname.matches("^[가-힣a-zA-Z0-9]+$")) {
            return NicknameCheckResponse.ofUnavailable(ReasonCode.INVALID_FORMAT);
        }

        if (forbiddenWordService.containsForbiddenWord(nickname, ForbiddenWordType.NICKNAME)) {
            return NicknameCheckResponse.ofUnavailable(ReasonCode.FORBIDDEN_WORD);
        }

        // 본인 닉네임 제외
        if (currentUserId != null) {
            User currentUser = userRepository.findById(currentUserId).orElse(null);
            if (currentUser != null && nickname.equals(currentUser.getNickname())) {
                return NicknameCheckResponse.ofAvailable();
            }
        }

        if (userRepository.existsByNickname(nickname)) {
            return NicknameCheckResponse.ofUnavailable(ReasonCode.DUPLICATE);
        }

        return NicknameCheckResponse.ofAvailable();
    }

    // 닉네임 검증 (내부용)
    private void validateNicknameForUpdate(String nickname) {
        if (forbiddenWordService.containsForbiddenWord(nickname, ForbiddenWordType.NICKNAME)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_WORD);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
        }
    }
}
