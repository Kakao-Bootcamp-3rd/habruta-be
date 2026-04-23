package com.imyme.mine.domain.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.challenge.dto.response.ChallengeHistoryResponse;
import com.imyme.mine.domain.challenge.dto.response.ChallengeRankingResponse;
import com.imyme.mine.domain.challenge.dto.response.TodayChallengeResponse;
import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeRanking;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 챌린지 읽기 전용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String REDIS_SUBMITTED_COUNT_KEY = "challenge:%d:submitted_count";

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeRankingRepository challengeRankingRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // ===== 오늘의 챌린지 =====

    /**
     * 오늘의 챌린지 조회
     */
    public TodayChallengeResponse getTodayChallenge(Long userId) {
        LocalDate today = LocalDate.now(KST);

        Challenge challenge = challengeRepository.findByChallengeDateWithKeyword(today)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        Optional<ChallengeAttempt> attemptOpt =
                challengeAttemptRepository.findByChallengeIdAndUserId(challenge.getId(), userId);

        TodayChallengeResponse.MyParticipation myParticipation = null;

        if (attemptOpt.isPresent()) {
            ChallengeAttempt attempt = attemptOpt.get();
            Integer score = null;
            Integer rank = null;

            if (challenge.getStatus() == ChallengeStatus.COMPLETED) {
                Optional<ChallengeRanking> rankingOpt =
                        challengeRankingRepository.findByChallengeIdAndUserId(challenge.getId(), userId);
                if (rankingOpt.isPresent()) {
                    score = rankingOpt.get().getScore();
                    rank = rankingOpt.get().getRankNo();
                }
            }

            myParticipation = TodayChallengeResponse.MyParticipation.builder()
                    .attemptId(attempt.getId())
                    .status(attempt.getStatus())
                    .score(score)
                    .rank(rank)
                    .build();
        }

        int participantCount = challenge.getStatus() == ChallengeStatus.COMPLETED
                ? challenge.getParticipantCount()
                : readSubmittedCount(challenge.getId());

        String message = challenge.getStatus() == ChallengeStatus.SCHEDULED
                ? "22:00에 시작됩니다."
                : null;

        return TodayChallengeResponse.builder()
                .id(challenge.getId())
                .keyword(TodayChallengeResponse.KeywordDto.builder()
                        .id(challenge.getKeyword().getId())
                        .name(challenge.getKeyword().getName())
                        .build())
                .challengeDate(challenge.getChallengeDate())
                .startAt(challenge.getStartAt())
                .endAt(challenge.getEndAt())
                .status(challenge.getStatus())
                .participantCount(participantCount)
                .myParticipation(myParticipation)
                .message(message)
                .build();
    }

    // ===== 랭킹 조회 =====

    /**
     * 챌린지 랭킹 조회 (offset 페이지네이션)
     */
    public ChallengeRankingResponse getRankings(Long challengeId, Long userId, int page, int size) {
        Challenge challenge = challengeRepository.findByIdWithKeyword(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        if (challenge.getStatus() != ChallengeStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_COMPLETED);
        }

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ChallengeRanking> rankingPage =
                challengeRankingRepository.findByChallengeIdOrderByRankNoAsc(challengeId, pageable);

        Optional<ChallengeRanking> myRankingOpt =
                challengeRankingRepository.findByChallengeIdAndUserId(challengeId, userId);

        int participantCount = challenge.getParticipantCount();

        List<ChallengeRankingResponse.RankingItem> rankingItems = rankingPage.getContent().stream()
                .map(r -> ChallengeRankingResponse.RankingItem.builder()
                        .rank(r.getRankNo())
                        .userId(r.getUser() != null ? r.getUser().getId() : null)
                        .nickname(r.getUserNickname())
                        .profileImageUrl(r.getUserProfileImageUrl())
                        .score(r.getScore())
                        .isMe(r.getUser() != null && r.getUser().getId().equals(userId))
                        .build())
                .collect(Collectors.toList());

        ChallengeRankingResponse.MyRank myRank = myRankingOpt.map(r ->
                ChallengeRankingResponse.MyRank.builder()
                        .rank(r.getRankNo())
                        .score(r.getScore())
                        .percentile(calculatePercentile(r.getRankNo(), participantCount))
                        .build()
        ).orElse(null);

        return ChallengeRankingResponse.builder()
                .challenge(ChallengeRankingResponse.ChallengeInfo.builder()
                        .id(challenge.getId())
                        .keywordName(challenge.getKeyword().getName())
                        .challengeDate(challenge.getChallengeDate())
                        .status(challenge.getStatus())
                        .participantCount(participantCount)
                        .build())
                .rankings(rankingItems)
                .pagination(ChallengeRankingResponse.Pagination.builder()
                        .currentPage(page)
                        .totalPages(rankingPage.getTotalPages())
                        .totalCount(rankingPage.getTotalElements())
                        .size(size)
                        .hasNext(rankingPage.hasNext())
                        .hasPrevious(rankingPage.hasPrevious())
                        .build())
                .myRank(myRank)
                .build();
    }

    /**
     * 가장 최근 완료된 챌린지 랭킹 조회
     */
    public ChallengeRankingResponse getLatestRankings(Long userId, int page, int size) {
        Challenge challenge = challengeRepository.findLatestCompletedWithKeyword()
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        return getRankings(challenge.getId(), userId, page, size);
    }

    // ===== 히스토리 조회 =====

    /**
     * 챌린지 히스토리 조회 (cursor 기반 페이지네이션)
     */
    public ChallengeHistoryResponse getChallengeHistory(
            Long userId, String cursor, int size, ChallengeStatus status, boolean participated) {

        ChallengeHistoryCursor decoded = decodeCursor(cursor);
        // IS NULL 조건 대신 sentinel 값으로 첫 페이지 처리 (PostgreSQL 42P18 회피)
        LocalDate cursorDate = decoded != null ? decoded.challengeDate() : LocalDate.of(9999, 12, 31);
        Long cursorId = decoded != null ? decoded.id() : Long.MAX_VALUE;

        // size+1 조회로 hasNext 판별
        Pageable pageable = PageRequest.of(0, size + 1);

        List<Challenge> challenges = fetchHistoryPage(userId, status, participated, cursorDate, cursorId, pageable);

        boolean hasNext = challenges.size() > size;
        if (hasNext) {
            challenges = challenges.subList(0, size);
        }

        if (challenges.isEmpty()) {
            return ChallengeHistoryResponse.builder()
                    .challenges(List.of())
                    .meta(ChallengeHistoryResponse.Meta.builder()
                            .size(0)
                            .hasNext(false)
                            .nextCursor(null)
                            .build())
                    .build();
        }

        List<Long> challengeIds = challenges.stream().map(Challenge::getId).collect(Collectors.toList());

        // bulk 조회
        Map<Long, ChallengeAttempt> attemptMap = challengeAttemptRepository
                .findByChallengeIdInAndUserId(challengeIds, userId).stream()
                .collect(Collectors.toMap(a -> a.getChallenge().getId(), a -> a));

        Map<Long, ChallengeRanking> rankingMap = challengeRankingRepository
                .findByChallengeIdInAndUserId(challengeIds, userId).stream()
                .collect(Collectors.toMap(r -> r.getChallenge().getId(), r -> r));

        List<ChallengeHistoryResponse.ChallengeItem> items = challenges.stream()
                .map(c -> buildChallengeItem(c, attemptMap.get(c.getId()), rankingMap.get(c.getId())))
                .collect(Collectors.toList());

        Challenge last = challenges.get(challenges.size() - 1);
        String nextCursor = hasNext ? encodeCursor(last.getChallengeDate(), last.getId()) : null;

        return ChallengeHistoryResponse.builder()
                .challenges(items)
                .meta(ChallengeHistoryResponse.Meta.builder()
                        .size(items.size())
                        .hasNext(hasNext)
                        .nextCursor(nextCursor)
                        .build())
                .build();
    }

    private List<Challenge> fetchHistoryPage(
            Long userId, ChallengeStatus status, boolean participated,
            LocalDate cursorDate, Long cursorId, Pageable pageable) {

        if (participated && status != null) {
            return challengeRepository.findParticipatedHistoryPageByStatus(userId, status, cursorDate, cursorId, pageable);
        } else if (participated) {
            return challengeRepository.findParticipatedHistoryPage(userId, cursorDate, cursorId, pageable);
        } else if (status != null) {
            return challengeRepository.findHistoryPageByStatus(status, cursorDate, cursorId, pageable);
        } else {
            return challengeRepository.findHistoryPage(cursorDate, cursorId, pageable);
        }
    }

    private ChallengeHistoryResponse.ChallengeItem buildChallengeItem(
            Challenge challenge, ChallengeAttempt attempt, ChallengeRanking ranking) {

        int participantCount = challenge.getStatus() == ChallengeStatus.COMPLETED
                ? challenge.getParticipantCount()
                : 0;

        ChallengeHistoryResponse.MyParticipation myParticipation = null;
        if (attempt != null && ranking != null) {
            myParticipation = ChallengeHistoryResponse.MyParticipation.builder()
                    .score(ranking.getScore())
                    .rank(ranking.getRankNo())
                    .percentile(calculatePercentile(ranking.getRankNo(), participantCount))
                    .build();
        }

        return ChallengeHistoryResponse.ChallengeItem.builder()
                .id(challenge.getId())
                .keywordName(challenge.getKeyword().getName())
                .challengeDate(challenge.getChallengeDate())
                .status(challenge.getStatus())
                .participantCount(participantCount)
                .myParticipation(myParticipation)
                .build();
    }

    // ===== Cursor 유틸 =====

    private record ChallengeHistoryCursor(LocalDate challengeDate, Long id) {}

    private ChallengeHistoryCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String json = new String(Base64.getDecoder().decode(cursor));
            return objectMapper.readValue(json, ChallengeHistoryCursor.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    private String encodeCursor(LocalDate challengeDate, Long id) {
        try {
            String json = objectMapper.writeValueAsString(new ChallengeHistoryCursor(challengeDate, id));
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            log.error("cursor 인코딩 실패: challengeDate={}, id={}", challengeDate, id, e);
            return null;
        }
    }

    // ===== 공통 유틸 =====

    private int readSubmittedCount(Long challengeId) {
        String val = stringRedisTemplate.opsForValue()
                .get(String.format(REDIS_SUBMITTED_COUNT_KEY, challengeId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    private double calculatePercentile(int rank, int participantCount) {
        if (participantCount <= 0) return 0.0;
        return ((participantCount - rank + 1) * 100.0) / participantCount;
    }
}