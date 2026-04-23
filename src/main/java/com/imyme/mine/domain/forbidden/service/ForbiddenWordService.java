package com.imyme.mine.domain.forbidden.service;

import com.imyme.mine.domain.forbidden.entity.ForbiddenWord;
import com.imyme.mine.domain.forbidden.entity.ForbiddenWordType;
import com.imyme.mine.domain.forbidden.repository.ForbiddenWordRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 금지어 서비스
 * - 서버 시작 시 금지어 목록을 메모리에 캐싱
 * - 닉네임 등에서 금지어 포함 여부 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForbiddenWordService {

    private final ForbiddenWordRepository forbiddenWordRepository;

    // 타입별로 금지어를 나누어 캐싱 (Key: Type, Value: 소문자 금지어 집합)
    private Map<ForbiddenWordType, Set<String>> forbiddenWordsCache = new HashMap<>();

    // 서버 시작 시 금지어 목록을 메모리에 로드
    @PostConstruct
    public void init() {
        loadForbiddenWords();
    }

    /**
     * 금지어 목록 로드 및 캐싱 (성능 최적화)
     * - DB 조회 후 타입별로 분류
     * - 검색 속도를 위해 미리 toLowerCase() 변환하여 저장
     */
    public void loadForbiddenWords() {
        List<ForbiddenWord> allWords = forbiddenWordRepository.findAll();
        Map<ForbiddenWordType, Set<String>> newCache = new HashMap<>();

        for (ForbiddenWord fw : allWords) {
            // Null-safe 처리 및 소문자 변환
            if (fw.getWord() == null) continue;

            String lowerWord = fw.getWord().toLowerCase();
            ForbiddenWordType type = fw.getType() != null ? fw.getType() : ForbiddenWordType.COMMON;

            newCache.computeIfAbsent(type, k -> new HashSet<>()).add(lowerWord);
        }

        this.forbiddenWordsCache = newCache;
        log.info("금지어 캐시 로드 완료: 총 {}개", allWords.size());
    }

    // 금지어 포함 여부 확인
    public boolean containsForbiddenWord(String text, ForbiddenWordType type) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase(); // 검사 대상도 한 번만 변환

        if (isContainInSet(lowerText, ForbiddenWordType.COMMON)) {
            return true;
        }
        if (type != ForbiddenWordType.COMMON) {
            return isContainInSet(lowerText, type);
        }

        return false;
    }

    private boolean isContainInSet(String text, ForbiddenWordType type) {
        Set<String> words = forbiddenWordsCache.getOrDefault(type, Collections.emptySet());

        // 단순 contains 반복 (데이터 많아지면 아호코라식 알고리즘 도입 고려)
        for (String forbiddenWord : words) {
            if (text.contains(forbiddenWord)) {
                log.warn("금지어 감지: type={}, word={}", type, forbiddenWord);
                return true;
            }
        }
        return false;
    }

    // 금지어 캐시 갱신 (관리자 기능용)
    public void refreshCache() {
        loadForbiddenWords();
    }
}
