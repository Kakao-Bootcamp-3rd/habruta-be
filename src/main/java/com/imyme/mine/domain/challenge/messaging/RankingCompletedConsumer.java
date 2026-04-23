package com.imyme.mine.domain.challenge.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.challenge.dto.message.ChallengeRankingCompletedDto;
import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeRanking;
import com.imyme.mine.domain.challenge.entity.ChallengeResult;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeResultRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import com.rabbitmq.client.Channel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 챌린지 토너먼트 최종 완료 MQ Consumer
 *
 * <p>AI 서버가 토너먼트를 마치면 {@code challenge.final.done} 큐로 경량 메시지를 발행.
 * 실제 랭킹·피드백 데이터는 AI가 미리 Redis에 저장해둔 키에서 직접 읽음:
 * <ul>
 *   <li>{@code challenge:{id}:final_ranking} — LRANGE → 순위별 attemptId</li>
 *   <li>{@code challenge:{id}:feedbacks}     — HGETALL → attemptId별 {userId,rank,score,feedbackJson}</li>
 * </ul>
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>job_id에서 challengeId 추출 ("job:123" → 123)</li>
 *   <li>Redis에서 최종 랭킹 + 피드백 일괄 조회</li>
 *   <li>ChallengeRanking Bulk INSERT (1위~N위)</li>
 *   <li>ChallengeResult Bulk INSERT (score, feedbackJson, modelVersion)</li>
 *   <li>ChallengeAttempt.markCompleted() 일괄 전환</li>
 *   <li>Challenge.complete() → status COMPLETED + participantCount + resultSummaryJson</li>
 *   <li>Redis 정리</li>
 *   <li>커밋 후 CHALLENGE_PERSONAL_RESULT / CHALLENGE_OVERALL_RESULT 알림 발송</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCompletedConsumer {

    private static final String REDIS_FINAL_RANKING_KEY   = "challenge:%d:final_ranking";
    private static final String REDIS_FEEDBACKS_KEY       = "challenge:%d:feedbacks";
    private static final String REDIS_PARTICIPANTS_KEY    = "challenge:%d:participants";
    private static final String REDIS_SUBMITTED_COUNT_KEY = "challenge:%d:submitted_count";

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeRankingRepository challengeRankingRepository;
    private final ChallengeResultRepository challengeResultRepository;
    private final UserRepository userRepository;
    private final NotificationCreatorService notificationCreatorService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{challengeMqProperties.queue.finalDone}")
    @Transactional
    public void handleFinalDone(Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[Ranking MQ] 토너먼트 최종 완료 수신: deliveryTag={}", deliveryTag);

            ChallengeRankingCompletedDto dto =
                    objectMapper.readValue(message.getBody(), ChallengeRankingCompletedDto.class);

            processRankingCompleted(dto);

            channel.basicAck(deliveryTag, false);
            log.info("[Ranking MQ] 처리 완료 (Ack): deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("[Ranking MQ] 처리 실패: deliveryTag={}", deliveryTag, e);
            try {
                channel.basicNack(deliveryTag, false, false);
                log.warn("[Ranking MQ] Nack → DLQ: deliveryTag={}", deliveryTag);
            } catch (IOException ioException) {
                log.error("[Ranking MQ] Nack 실패", ioException);
            }
        }
    }

    private void processRankingCompleted(ChallengeRankingCompletedDto dto) {
        // "job:123" → 123L
        Long challengeId = parseChallengeId(dto.getJobId());
        if (challengeId == null) {
            log.warn("[Ranking MQ] job_id 파싱 실패: jobId={}", dto.getJobId());
            return;
        }

        // 멱등성 보호: 이미 랭킹이 저장된 경우 스킵
        if (challengeRankingRepository.existsByChallengeId(challengeId)) {
            log.info("[Ranking MQ] 스킵 (이미 처리됨): challengeId={}", challengeId);
            return;
        }

        // Redis에서 최종 랭킹 + 피드백 조회
        List<Long> rankedAttemptIds = readFinalRanking(challengeId);
        if (rankedAttemptIds.isEmpty()) {
            log.warn("[Ranking MQ] final_ranking 없음: challengeId={}", challengeId);
            return;
        }

        Map<Long, FeedbackEntry> feedbackMap = readFeedbacks(challengeId);

        // DB 배치 조회
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalStateException("Challenge not found: " + challengeId));

        Map<Long, ChallengeAttempt> attemptMap = challengeAttemptRepository
                .findAllById(rankedAttemptIds).stream()
                .collect(Collectors.toMap(ChallengeAttempt::getId, a -> a));

        List<Long> userIds = feedbackMap.values().stream()
                .map(FeedbackEntry::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // ChallengeRanking + ChallengeResult Bulk INSERT
        List<ChallengeRanking> rankings = new ArrayList<>(rankedAttemptIds.size());
        List<ChallengeResult> results = new ArrayList<>(rankedAttemptIds.size());
        for (int i = 0; i < rankedAttemptIds.size(); i++) {
            Long attemptId = rankedAttemptIds.get(i);
            int rankNo = i + 1;
            FeedbackEntry fb = feedbackMap.get(attemptId);
            ChallengeAttempt attempt = attemptMap.get(attemptId);
            User user = fb != null ? userMap.get(fb.getUserId()) : null;

            rankings.add(ChallengeRanking.builder()
                    .challenge(challenge)
                    .user(user)
                    .attempt(attempt)
                    .rankNo(rankNo)
                    .score(fb != null && fb.getScore() != null ? fb.getScore() : 0)
                    .userNickname(user != null ? user.getNickname() : "")
                    .userProfileImageUrl(user != null ? user.getProfileImageUrl() : null)
                    .build());

            if (attempt != null) {
                results.add(ChallengeResult.builder()
                        .attempt(attempt)
                        .score(fb != null && fb.getScore() != null ? fb.getScore() : 0)
                        .feedbackJson(fb != null && fb.getFeedbackJson() != null ? fb.getFeedbackJson() : "{}")
                        .modelVersion(fb != null && fb.getModelVersion() != null ? fb.getModelVersion() : "unknown")
                        .build());
                attempt.markCompleted();
            }
        }
        challengeRankingRepository.saveAll(rankings);
        challengeResultRepository.saveAll(results);

        // Challenge COMPLETED 전환
        ChallengeAttempt bestAttempt = attemptMap.get(rankedAttemptIds.get(0));
        String resultSummaryJson = buildSummaryJson(rankedAttemptIds, feedbackMap, userMap);
        challenge.complete(bestAttempt, resultSummaryJson, rankedAttemptIds.size());

        // Redis 정리
        cleanupRedis(challengeId);

        log.info("[Ranking MQ] COMPLETED 전환 완료: challengeId={}, 참가자={}",
                challengeId, rankedAttemptIds.size());

        // 커밋 후 개인/전체 결과 알림
        List<Long> notifyUserIds = userIds;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long userId : notifyUserIds) {
                    notificationCreatorService.create(
                            userId,
                            NotificationType.CHALLENGE_PERSONAL_RESULT,
                            "내 챌린지 결과가 나왔어요!",
                            "상세 피드백을 확인해보세요.",
                            challengeId,
                            "CHALLENGE"
                    );
                    notificationCreatorService.create(
                            userId,
                            NotificationType.CHALLENGE_OVERALL_RESULT,
                            "챌린지 최종 랭킹이 나왔어요!",
                            "내 순위를 확인해보세요.",
                            challengeId,
                            "CHALLENGE"
                    );
                }
                log.info("[Ranking MQ] 결과 알림 발송 완료: challengeId={}, 대상={}",
                        challengeId, notifyUserIds.size());
            }
        });
    }

    // ===== Redis 읽기 =====

    private List<Long> readFinalRanking(Long challengeId) {
        String key = String.format(REDIS_FINAL_RANKING_KEY, challengeId);
        List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    private Map<Long, FeedbackEntry> readFeedbacks(Long challengeId) {
        String key = String.format(REDIS_FEEDBACKS_KEY, challengeId);
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
        Map<Long, FeedbackEntry> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            try {
                Long attemptId = Long.parseLong(entry.getKey().toString());
                FeedbackEntry fb = objectMapper.readValue(entry.getValue().toString(), FeedbackEntry.class);
                result.put(attemptId, fb);
            } catch (Exception e) {
                log.error("[Ranking MQ] feedbacks 파싱 실패: key={}", entry.getKey(), e);
            }
        }
        return result;
    }

    // ===== Redis 정리 =====

    private void cleanupRedis(Long challengeId) {
        Set<String> pairsKeys = stringRedisTemplate.keys("pairs:job:" + challengeId + ":*");
        if (pairsKeys != null && !pairsKeys.isEmpty()) {
            stringRedisTemplate.delete(pairsKeys);
        }
        stringRedisTemplate.delete(String.format(REDIS_PARTICIPANTS_KEY, challengeId));
        stringRedisTemplate.delete(String.format(REDIS_FINAL_RANKING_KEY, challengeId));
        stringRedisTemplate.delete(String.format(REDIS_FEEDBACKS_KEY, challengeId));
        stringRedisTemplate.delete(String.format(REDIS_SUBMITTED_COUNT_KEY, challengeId));
    }

    // ===== 유틸 =====

    private Long parseChallengeId(String jobId) {
        if (jobId == null) return null;
        try {
            return Long.parseLong(jobId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildSummaryJson(List<Long> rankedAttemptIds,
                                    Map<Long, FeedbackEntry> feedbackMap,
                                    Map<Long, User> userMap) {
        List<Map<String, Object>> top3 = new ArrayList<>();
        int limit = Math.min(3, rankedAttemptIds.size());
        for (int i = 0; i < limit; i++) {
            Long attemptId = rankedAttemptIds.get(i);
            FeedbackEntry fb = feedbackMap.get(attemptId);
            User user = fb != null ? userMap.get(fb.getUserId()) : null;
            Map<String, Object> entry = new HashMap<>();
            entry.put("rank", i + 1);
            entry.put("user_id", fb != null ? fb.getUserId() : null);
            entry.put("nickname", user != null ? user.getNickname() : null);
            entry.put("score", fb != null && fb.getScore() != null ? fb.getScore() : 0);
            entry.put("attempt_id", attemptId);
            top3.add(entry);
        }

        double avgScore = feedbackMap.values().stream()
                .mapToInt(fb -> fb.getScore() != null ? fb.getScore() : 0)
                .average().orElse(0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("total_participants", rankedAttemptIds.size());
        summary.put("average_score", Math.round(avgScore * 10.0) / 10.0);

        Map<String, Object> result = new HashMap<>();
        result.put("top3", top3);
        result.put("summary", summary);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("[Ranking MQ] resultSummaryJson 직렬화 실패", e);
            return "{}";
        }
    }

    // ===== AI feedbacks Hash 역직렬화용 내부 DTO =====

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FeedbackEntry {
        @JsonProperty("user_id")
        private Long userId;
        private Integer rank;
        private Integer score;
        @JsonProperty("feedback_json")
        private String feedbackJson;
        @JsonProperty("model_version")
        private String modelVersion;
    }
}