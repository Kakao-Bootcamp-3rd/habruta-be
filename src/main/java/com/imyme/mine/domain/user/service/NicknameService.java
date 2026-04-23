package com.imyme.mine.domain.user.service;

import com.imyme.mine.domain.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 닉네임 중복 체크 서비스 (Redis SET 기반)
 * - Race Condition 완벽 방지 (Atomic Operation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NicknameService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String NICKNAME_SET_KEY = "nickname:set";

    /**
     * 애플리케이션 시작 시 모든 닉네임을 Redis Set에 로드
     */
    @PostConstruct
    public void initializeNicknameCache() {
        log.info("닉네임 캐시 초기화 시작...");

        List<String> allNicknames = userRepository.findAllNicknames();

        if (!allNicknames.isEmpty()) {
            redisTemplate.opsForSet().add(
                NICKNAME_SET_KEY,
                allNicknames.toArray(new String[0])
            );
            log.info("닉네임 캐시 초기화 완료: {} 개", allNicknames.size());
        }
    }

    /**
     * 닉네임 중복 체크 및 선점을 동시에 처리 (Atomic Operation)
     * ⚠️ Race Condition 완벽 방지
     *
     * @param nickname 체크할 닉네임
     * @return true: 사용 가능(선점 성공), false: 이미 존재함(중복)
     */
    public boolean tryReserveNickname(String nickname) {
        // Redis SET의 add 명령은 원자적(Atomic)으로 동작
        // 이미 존재하면 0 반환, 추가 성공하면 1 반환
        // → 100명이 동시에 같은 닉네임으로 시도해도 단 1명만 성공!
        Long result = redisTemplate.opsForSet().add(NICKNAME_SET_KEY, nickname);
        return result != null && result > 0;
    }

    /**
     * 닉네임 삭제 (탈퇴 시)
     */
    @Transactional
    public void removeNickname(String nickname) {
        redisTemplate.opsForSet().remove(NICKNAME_SET_KEY, nickname);
    }

    /**
     * 닉네임 변경 (변경 시)
     */
    @Transactional
    public void updateNickname(String oldNickname, String newNickname) {
        // 1. 기존 닉네임 제거
        redisTemplate.opsForSet().remove(NICKNAME_SET_KEY, oldNickname);

        // 2. 새 닉네임 추가
        redisTemplate.opsForSet().add(NICKNAME_SET_KEY, newNickname);
    }
}