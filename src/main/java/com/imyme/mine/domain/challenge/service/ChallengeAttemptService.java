package com.imyme.mine.domain.challenge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.challenge.dto.request.CompleteUploadRequest;
import com.imyme.mine.domain.challenge.dto.request.CreateAttemptRequest;
import com.imyme.mine.domain.challenge.dto.response.CreateAttemptResponse;
import com.imyme.mine.domain.challenge.dto.response.MyResultResponse;
import com.imyme.mine.domain.challenge.dto.response.UploadCompleteResponse;
import com.imyme.mine.domain.challenge.entity.Challenge;
import com.imyme.mine.domain.challenge.entity.ChallengeAttempt;
import com.imyme.mine.domain.challenge.entity.ChallengeAttemptStatus;
import com.imyme.mine.domain.challenge.entity.ChallengeRanking;
import com.imyme.mine.domain.challenge.entity.ChallengeResult;
import com.imyme.mine.domain.challenge.entity.ChallengeStatus;
import com.imyme.mine.domain.challenge.repository.ChallengeAttemptRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRankingRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeRepository;
import com.imyme.mine.domain.challenge.repository.ChallengeResultRepository;
import com.imyme.mine.domain.storage.service.StorageService;
import com.imyme.mine.global.config.ChallengeMqProperties;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeAttemptService {

    private static final int UPLOAD_URL_EXPIRES_IN = 300; // 5분 (초)
    private static final String REDIS_ACTIVE_STT_KEY = "challenge:%d:active_stt_count";
    private static final String REDIS_PENDING_UPLOADS_KEY = "challenge:%d:pending_uploads";
    private static final String REDIS_SUBMITTED_COUNT_KEY = "challenge:%d:submitted_count";
    private static final Duration STT_KEY_TTL = Duration.ofHours(4);

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository challengeAttemptRepository;
    private final ChallengeRankingRepository challengeRankingRepository;
    private final ChallengeResultRepository challengeResultRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChallengeMqProperties mqProperties;
    private final ChallengeGateService challengeGateService;
    private final ChallengeParticipantSseService challengeParticipantSseService;

    // ===== 참여 시작 =====

    /**
     * 챌린지 참여 시작 — attempt 생성 또는 PENDING 재사용
     *
     * @return (response, isCreated) — isCreated=true면 HTTP 201, false면 HTTP 200
     */
    @Transactional
    public Map.Entry<CreateAttemptResponse, Boolean> createAttempt(
            Long challengeId, Long userId, CreateAttemptRequest request) {

        Challenge challenge = challengeRepository.findByIdWithKeyword(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        validateChallengeOpenForAttempt(challenge);

        Optional<ChallengeAttempt> existing =
                challengeAttemptRepository.findByChallengeIdAndUserId(challengeId, userId);

        if (existing.isPresent()) {
            ChallengeAttempt attempt = existing.get();
            if (attempt.getStatus() != ChallengeAttemptStatus.PENDING) {
                throw new BusinessException(ErrorCode.ALREADY_PARTICIPATED);
            }
            // PENDING 재사용: 기존 objectKey 재사용 → orphan 파일 방지
            // audioKey가 이미 있으면 동일 key로 presigned URL만 재발급, 없으면 신규 생성
            String uploadUrl;
            if (attempt.getAudioKey() != null) {
                uploadUrl = storageService.reissueChallengePresignedUrl(
                        attempt.getAudioKey(), request.contentType());
            } else {
                String[] ticket = storageService.generateChallengePresignedUrl(
                        userId, challengeId, attempt.getId(), request.contentType());
                attempt.refreshUploadReservation(ticket[0]);
                uploadUrl = ticket[1];
            }

            return Map.entry(buildCreateAttemptResponse(attempt, challengeId, uploadUrl), false);
        }

        // 신규 생성
        User user = userRepository.getReferenceById(userId);
        ChallengeAttempt attempt = ChallengeAttempt.builder()
                .challenge(challenge)
                .user(user)
                .build();
        challengeAttemptRepository.save(attempt);

        String[] ticket = storageService.generateChallengePresignedUrl(
                userId, challengeId, attempt.getId(), request.contentType());
        attempt.refreshUploadReservation(ticket[0]);

        log.info("[Challenge] 참여 시작 - challengeId={}, userId={}, attemptId={}",
                challengeId, userId, attempt.getId());

        return Map.entry(buildCreateAttemptResponse(attempt, challengeId, ticket[1]), true);
    }

    // ===== 업로드 완료 확정 =====

    /**
     * 업로드 완료 확정 — PENDING → UPLOADED
     */
    @Transactional
    public UploadCompleteResponse completeUpload(
            Long challengeId, Long attemptId, Long userId, CompleteUploadRequest request) {

        ChallengeAttempt attempt = challengeAttemptRepository
                .findByIdAndChallengeIdAndUserId(attemptId, challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        if (attempt.getStatus() != ChallengeAttemptStatus.PENDING) {
            // 이미 UPLOADED/PROCESSING/COMPLETED/FAILED → 업로드 재시도 불가
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }

        Challenge challenge = attempt.getChallenge();
        validateChallengeAcceptingUploadCompletion(challenge);

        if (!request.objectKey().equals(attempt.getAudioKey())) {
            throw new BusinessException(ErrorCode.INVALID_OBJECT_KEY);
        }

        storageService.validateChallengeObjectMetadata(request.objectKey());

        attempt.markUploadCompleted(request.objectKey(), request.durationSeconds());

        log.info("[Challenge] 업로드 완료 - challengeId={}, userId={}, attemptId={}",
                challengeId, userId, attemptId);

        // Eager STT: 업로드 완료 즉시 STT MQ 발행 (22:11:30 게이트까지 기다리지 않음)
        boolean isClosed = challenge.getStatus() == ChallengeStatus.CLOSED;
        String audioUrl = storageService.generatePresignedGetUrl(request.objectKey());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 1. STT MQ 발행
                Map<String, Object> payload = new HashMap<>();
                payload.put("attemptId", attemptId);
                payload.put("challengeId", challengeId);
                payload.put("audioUrl", audioUrl);
                rabbitTemplate.convertAndSend(
                        mqProperties.getExchange(),
                        mqProperties.getRouting().getFeedbackRequest(),
                        payload
                );

                // 2. active_stt_count INCR (in-flight STT 수 추적)
                String sttKey = String.format(REDIS_ACTIVE_STT_KEY, challengeId);
                stringRedisTemplate.opsForValue().increment(sttKey);
                stringRedisTemplate.expire(sttKey, STT_KEY_TTL);

                log.info("[Challenge] Eager STT MQ 발행: challengeId={}, attemptId={}", challengeId, attemptId);

                // 3. submitted_count INCR (오늘의 챌린지 참여자 수 실시간 제공)
                String submittedKey = String.format(REDIS_SUBMITTED_COUNT_KEY, challengeId);
                stringRedisTemplate.opsForValue().increment(submittedKey);
                stringRedisTemplate.expire(submittedKey, STT_KEY_TTL);

                // 4. 참여자 수 SSE 브로드캐스트
                challengeParticipantSseService.broadcast(challengeId);

                // 5. CLOSED 상태 upload-complete: pending_uploads DECR → 0이면 조기 게이트 종료
                if (isClosed) {
                    String pendingKey = String.format(REDIS_PENDING_UPLOADS_KEY, challengeId);
                    Long remaining = stringRedisTemplate.opsForValue().decrement(pendingKey);
                    log.info("[Challenge] pending_uploads DECR: challengeId={}, remaining={}", challengeId, remaining);
                    if (remaining != null && remaining <= 0) {
                        log.info("[Challenge] 모든 업로드 수신 → 조기 게이트 종료: challengeId={}", challengeId);
                        challengeGateService.closeGate(challengeId);
                    }
                }
            }
        });

        return UploadCompleteResponse.builder()
                .attemptId(attempt.getId())
                .status(attempt.getStatus())
                .submittedAt(attempt.getSubmittedAt())
                .message("제출이 완료되었습니다. 결과 집계를 기다려주세요.")
                .build();
    }

    // ===== 내 결과 조회 =====

    /**
     * 내 챌린지 결과 조회
     */
    @Transactional(readOnly = true)
    public MyResultResponse getMyResult(Long challengeId, Long userId) {
        ChallengeAttempt attempt = challengeAttemptRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));

        Challenge challenge = challengeRepository.findByIdWithKeyword(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        if (challenge.getStatus() != ChallengeStatus.COMPLETED) {
            return MyResultResponse.builder()
                    .challengeId(challengeId)
                    .keywordName(challenge.getKeyword().getName())
                    .challengeDate(challenge.getChallengeDate())
                    .status(challenge.getStatus())
                    .message("AI 분석 중입니다. 잠시만 기다려주세요.")
                    // 스케줄러는 22:12 ANALYZING 시작을 보장하나 완료 시각은 AI 처리량에 따라 달라짐
                    // endAt + 15분은 UI 표시용 추정치이며 실제 완료를 보장하지 않음
                    .expectedCompletionAt(challenge.getEndAt().plusMinutes(15))
                    .build();
        }

        Optional<ChallengeResult> resultOpt = challengeResultRepository.findByAttemptId(attempt.getId());
        Optional<ChallengeRanking> rankingOpt =
                challengeRankingRepository.findByChallengeIdAndUserId(challengeId, userId);

        MyResultResponse.MyResult myResult = null;
        if (resultOpt.isPresent() && rankingOpt.isPresent()) {
            ChallengeResult result = resultOpt.get();
            ChallengeRanking ranking = rankingOpt.get();
            int participantCount = challenge.getParticipantCount();

            myResult = MyResultResponse.MyResult.builder()
                    .attemptId(attempt.getId())
                    .score(result.getScore())
                    .rank(ranking.getRankNo())
                    .percentile(calculatePercentile(ranking.getRankNo(), participantCount))
                    .durationSeconds(attempt.getDurationSeconds())
                    .sttText(attempt.getSttText())
                    .feedback(parseFeedbackJson(result.getFeedbackJson()))
                    .build();
        }

        return MyResultResponse.builder()
                .challengeId(challengeId)
                .keywordName(challenge.getKeyword().getName())
                .challengeDate(challenge.getChallengeDate())
                .status(challenge.getStatus())
                .myResult(myResult)
                .build();
    }

    // ===== Private 유틸 =====

    private void validateChallengeOpenForAttempt(Challenge challenge) {
        switch (challenge.getStatus()) {
            case SCHEDULED -> throw new BusinessException(ErrorCode.CHALLENGE_NOT_STARTED);
            case CLOSED, ANALYZING, COMPLETED -> throw new BusinessException(ErrorCode.CHALLENGE_ENDED);
            default -> { /* OPEN — 허용 */ }
        }
    }

    private void validateChallengeAcceptingUploadCompletion(Challenge challenge) {
        switch (challenge.getStatus()) {
            case SCHEDULED -> throw new BusinessException(ErrorCode.CHALLENGE_NOT_STARTED);
            case ANALYZING, COMPLETED -> throw new BusinessException(ErrorCode.CHALLENGE_ENDED);
            default -> { /* OPEN, CLOSED — 허용 */ }
        }
    }

    private CreateAttemptResponse buildCreateAttemptResponse(
            ChallengeAttempt attempt, Long challengeId, String uploadUrl) {
        return CreateAttemptResponse.builder()
                .attemptId(attempt.getId())
                .challengeId(challengeId)
                .uploadUrl(uploadUrl)
                .objectKey(attempt.getAudioKey())
                .expiresIn(UPLOAD_URL_EXPIRES_IN)
                .status(attempt.getStatus())
                .build();
    }

    private double calculatePercentile(int rank, int participantCount) {
        if (participantCount <= 0) return 0.0;
        return ((participantCount - rank + 1) * 100.0) / participantCount;
    }

    private Object parseFeedbackJson(String feedbackJson) {
        if (feedbackJson == null) return null;
        try {
            return objectMapper.readValue(feedbackJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Challenge] feedbackJson 파싱 실패 - raw string 반환");
            return feedbackJson;
        }
    }
}