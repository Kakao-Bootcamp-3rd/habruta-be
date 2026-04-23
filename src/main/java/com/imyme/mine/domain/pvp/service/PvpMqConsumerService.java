package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import com.imyme.mine.domain.pvp.dto.message.FeedbackRequestDto;
import com.imyme.mine.domain.pvp.dto.message.FeedbackResponseDto;
import com.imyme.mine.domain.pvp.dto.message.SttResponseDto;
import com.imyme.mine.domain.pvp.entity.*;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.domain.pvp.messaging.RabbitMQMessagePublisher;
import com.imyme.mine.domain.pvp.repository.PvpFeedbackRepository;
import com.imyme.mine.domain.pvp.repository.PvpHistoryRepository;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import com.imyme.mine.global.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RabbitMQ Consumer 비즈니스 로직 서비스
 * - STT Response / Feedback Response 처리
 * - Consumer에서 호출, @Transactional로 트랜잭션 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PvpMqConsumerService {

    private static final String MODEL_VERSION = "pvp-v1";

    private final PvpRoomRepository pvpRoomRepository;
    private final PvpSubmissionRepository pvpSubmissionRepository;
    private final PvpFeedbackRepository pvpFeedbackRepository;
    private final PvpHistoryRepository pvpHistoryRepository;
    private final UserRepository userRepository;
    private final RabbitMQMessagePublisher rabbitMQMessagePublisher;
    private final MessagePublisher messagePublisher;
    private final com.imyme.mine.domain.user.service.UserService userService;
    private final NotificationCreatorService notificationCreatorService;

    /**
     * STT Response 처리
     * - 성공: sttText 저장 + 양쪽 완료 시 Feedback Request 발행
     * - 실패: submission FAIL + 남은 1명 피드백 요청 발행
     */
    @Transactional
    public void handleSttResponse(SttResponseDto dto) {
        if (dto.getRoomId() == null || dto.getUserId() == null) {
            log.warn("[MQ] STT Response 무시: roomId 또는 userId가 null");
            return;
        }

        Long roomId = dto.getRoomId();
        Long userId = dto.getUserId();

        // Submission 조회
        PvpSubmission submission = pvpSubmissionRepository.findByRoomIdAndUserId(roomId, userId)
                .orElse(null);
        if (submission == null) {
            log.warn("[MQ] STT Response 무시: submission 없음 - roomId={}, userId={}", roomId, userId);
            return;
        }

        // Idempotent guard: UPLOADED 상태에서만 처리
        if (submission.getStatus() != PvpSubmissionStatus.UPLOADED) {
            log.info("[MQ] STT Response 스킵 (이미 처리됨): roomId={}, userId={}, status={}",
                    roomId, userId, submission.getStatus());
            return;
        }

        // FAIL 처리
        if ("FAIL".equalsIgnoreCase(dto.getStatus())) {
            submission.fail();
            pvpSubmissionRepository.save(submission);
            log.warn("[MQ] STT FAIL: roomId={}, userId={}, error={}", roomId, userId, dto.getError());

            // 남은 1명이 STT 완료(PROCESSING)이면 1명 피드백 요청 발행
            tryPublishFeedbackRequest(roomId);
            return;
        }

        // 성공 처리
        submission.saveSttText(dto.getSttText());
        submission.startProcessing();
        pvpSubmissionRepository.save(submission);
        log.info("[MQ] STT 성공: roomId={}, userId={}", roomId, userId);

        // 양쪽 STT 완료 또는 한쪽 FAIL + 한쪽 완료 시 Feedback Request 발행
        tryPublishFeedbackRequest(roomId);
    }

    /**
     * Feedback Request 발행 시도
     * - 양쪽 STT 완료(PROCESSING) 또는 한쪽 FAIL + 한쪽 완료 시 발행
     * - 비관적 락으로 중복 발행 방지
     */
    @Transactional
    public void tryPublishFeedbackRequest(Long roomId) {
        // 비관적 락으로 room 조회 (중복 발행 방지: SELECT ... FOR UPDATE)
        PvpRoom room = pvpRoomRepository.findByIdWithDetailsForUpdate(roomId).orElse(null);
        if (room == null) {
            log.warn("[MQ] Feedback Request 발행 실패: 방 없음 - roomId={}", roomId);
            return;
        }

        // 이미 CANCELED/FINISHED면 스킵
        if (room.getStatus() == PvpRoomStatus.CANCELED ||
            room.getStatus() == PvpRoomStatus.FINISHED) {
            return;
        }

        // feedbackRequestedAt 플래그로 중복 발행 차단
        if (room.isFeedbackRequested()) {
            log.info("[MQ] Feedback Request 스킵 (이미 발행됨): roomId={}", roomId);
            return;
        }

        List<PvpSubmission> submissions = pvpSubmissionRepository.findByRoomIdWithUser(roomId);

        // PROCESSING 상태인 submission (STT 완료)
        List<PvpSubmission> completedStt = submissions.stream()
                .filter(s -> s.getStatus() == PvpSubmissionStatus.PROCESSING)
                .toList();

        // FAILED 상태인 submission
        long failedCount = submissions.stream()
                .filter(s -> s.getStatus() == PvpSubmissionStatus.FAILED)
                .count();

        // 양쪽 모두 FAIL이면 게임 취소
        if (failedCount == 2) {
            room.cancel();
            pvpRoomRepository.save(room);
            broadcastAfterCommit(roomId,
                    PvpMessage.statusChange(roomId, PvpRoomStatus.CANCELED, "AI 분석에 실패했습니다."));
            log.warn("[MQ] 양쪽 STT 모두 FAIL → 게임 취소: roomId={}", roomId);
            return;
        }

        // 발행 조건: (양쪽 STT 완료) 또는 (한쪽 완료 + 한쪽 FAIL)
        boolean bothDone = completedStt.size() == 2;
        boolean oneCompleteOneFail = completedStt.size() == 1 && failedCount == 1;

        if (!bothDone && !oneCompleteOneFail) {
            return; // 아직 대기 중
        }

        // Feedback Request 생성 (항상 2명 포함)
        String keywordName = room.getKeyword() != null ? room.getKeyword().getName() : "";
        List<FeedbackRequestDto.UserAnswer> userAnswers = buildUserAnswers(room, submissions);

        if (userAnswers.size() != 2) {
            log.warn("[MQ] Feedback Request 스킵: users size != 2 - roomId={}, size={}",
                    roomId, userAnswers.size());
            return;
        }

        FeedbackRequestDto feedbackRequest = FeedbackRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .roomId(roomId)
                .timestamp(System.currentTimeMillis())
                .criteria(FeedbackRequestDto.Criteria.builder()
                        .keyword(keywordName)
                        .modelAnswer("")
                        .build())
                .users(userAnswers)
                .build();

        // 발행 시각 기록 후 publish
        room.markFeedbackRequested();
        pvpRoomRepository.save(room);

        rabbitMQMessagePublisher.publishFeedbackRequest(feedbackRequest);
        log.info("[MQ] Feedback Request 발행: roomId={}, userCount={}", roomId, userAnswers.size());
    }

    /**
     * Feedback Response 처리
     * - 성공: PvpFeedback 저장 + 승자 결정 + PvpHistory 생성 + 브로드캐스트
     * - 실패: submissions FAIL + room CANCEL
     */
    @Transactional
    public void handleFeedbackResponse(FeedbackResponseDto dto) {
        if (dto.getRoomId() == null) {
            log.warn("[MQ] Feedback Response 무시: roomId가 null");
            return;
        }

        Long roomId = dto.getRoomId();

        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId).orElse(null);
        if (room == null) {
            log.warn("[MQ] Feedback Response 무시: 방 없음 - roomId={}", roomId);
            return;
        }

        // Idempotent guard: 이미 종료된 방이면 스킵
        if (room.getStatus() == PvpRoomStatus.FINISHED ||
            room.getStatus() == PvpRoomStatus.CANCELED) {
            log.info("[MQ] Feedback Response 스킵 (이미 종료): roomId={}, status={}", roomId, room.getStatus());
            return;
        }

        // FAIL 처리
        if ("FAIL".equalsIgnoreCase(dto.getStatus())) {
            log.warn("[MQ] Feedback FAIL: roomId={}, error={}", roomId, dto.getError());
            handleFeedbackFail(room);
            return;
        }

        // feedbacks 검증
        if (dto.getFeedbacks() == null || dto.getFeedbacks().isEmpty()) {
            log.warn("[MQ] Feedback Response 무시: feedbacks가 비어있음 - roomId={}", roomId);
            handleFeedbackFail(room);
            return;
        }

        // feedbackMap 생성 (userId → feedback)
        Map<Long, FeedbackResponseDto.UserFeedback> feedbackMap = dto.getFeedbacks().stream()
                .collect(Collectors.toMap(FeedbackResponseDto.UserFeedback::getUserId, f -> f));

        // 방 참여자 목록
        User host = room.getHostUser();
        User guest = room.getGuestUser();
        List<User> participants = new java.util.ArrayList<>();
        if (host != null) participants.add(host);
        if (guest != null) participants.add(guest);

        // 피드백 저장 (참여자 기준, FAIL 유저도 포함)
        User winner = null;
        int highestScore = -1;

        for (User user : participants) {
            // Idempotent guard: 이미 피드백 존재하면 스킵
            if (pvpFeedbackRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
                log.info("[MQ] Feedback 스킵 (이미 존재): roomId={}, userId={}", roomId, user.getId());
                continue;
            }

            FeedbackResponseDto.UserFeedback fb = feedbackMap.get(user.getId());
            int score = (fb != null && fb.getScore() != null) ? fb.getScore() : 0;

            Map<String, Object> feedbackJson = (fb != null)
                    ? Map.of(
                        "summary", nullSafe(fb.getSummary()),
                        "keywords", fb.getKeywords() != null ? fb.getKeywords() : List.of(),
                        "facts", nullSafe(fb.getFacts()),
                        "understanding", nullSafe(fb.getUnderstanding()),
                        "personalizedFeedback", nullSafe(fb.getPersonalizedFeedback()))
                    : failFeedbackJson();

            PvpFeedback pvpFeedback = PvpFeedback.builder()
                    .room(room)
                    .user(user)
                    .score(score)
                    .pvpFeedbackJson(feedbackJson)
                    .modelVersion(MODEL_VERSION)
                    .build();
            pvpFeedbackRepository.save(pvpFeedback);

            // Submission 완료 처리
            pvpSubmissionRepository.findByRoomIdAndUserId(roomId, user.getId())
                    .ifPresent(submission -> {
                        if (submission.getStatus() != PvpSubmissionStatus.COMPLETED) {
                            submission.complete();
                            pvpSubmissionRepository.save(submission);
                        }
                    });

            // 승자 결정 (동점은 먼저 높은 점수에 도달한 유저 승리 - 무승부 없음)
            if (score > highestScore) {
                highestScore = score;
                winner = user;
            }
        }

        // 둘 다 0점이면 둘 다 패배 (무승부 없음, 아무도 이기지 않음)
        if (highestScore == 0) {
            winner = null;
        }

        // 방 종료
        room.finish(winner);
        pvpRoomRepository.save(room);

        // PvpHistory 생성
        createHistories(room, dto.getFeedbacks());

        log.info("[MQ] 게임 완료: roomId={}, winner={}", roomId,
                winner != null ? winner.getId() : "둘다패배(0점)");

        // PvP 결과 알림 (host, guest 모두 — 트랜잭션 커밋 후 FCM 비동기 발송)
        for (User participant : participants) {
            notificationCreatorService.create(
                participant.getId(),
                NotificationType.PVP_RESULT,
                "PvP 결과가 나왔어요!",
                "대결 결과를 확인해보세요.",
                roomId,
                "PVP_ROOM"
            );
        }

        // 브로드캐스트
        broadcastAfterCommit(roomId, PvpMessage.analysisCompleted(roomId));
    }

    /**
     * Feedback 실패 시 처리
     */
    private void handleFeedbackFail(PvpRoom room) {
        Long roomId = room.getId();

        // 모든 submission FAIL 처리
        List<PvpSubmission> submissions = pvpSubmissionRepository.findByRoomIdWithUser(roomId);
        for (PvpSubmission submission : submissions) {
            if (submission.getStatus() != PvpSubmissionStatus.FAILED
                    && submission.getStatus() != PvpSubmissionStatus.COMPLETED) {
                submission.fail();
                pvpSubmissionRepository.save(submission);
            }
        }

        room.cancel();
        pvpRoomRepository.save(room);

        broadcastAfterCommit(roomId,
                PvpMessage.statusChange(roomId, PvpRoomStatus.CANCELED, "AI 분석에 실패했습니다."));
    }

    /**
     * PvpHistory 2개 생성 (host, guest)
     */
    private void createHistories(PvpRoom room, List<FeedbackResponseDto.UserFeedback> feedbacks) {
        // userId → score 매핑
        Map<Long, Integer> scoreMap = feedbacks.stream()
                .collect(Collectors.toMap(
                        FeedbackResponseDto.UserFeedback::getUserId,
                        f -> f.getScore() != null ? f.getScore() : 0
                ));

        User hostUser = room.getHostUser();
        User guestUser = room.getGuestUser();
        User winnerUser = room.getWinnerUser();

        // 승자 winCount 증가 + 캐시 무효화
        if (winnerUser != null) {
            winnerUser.incrementWinCount();
            userRepository.save(winnerUser);
            userService.evictProfileCache(winnerUser.getId());
        }

        // 호스트 히스토리
        if (hostUser != null) {
            int hostScore = scoreMap.getOrDefault(hostUser.getId(), 0);
            boolean hostWon = winnerUser != null && winnerUser.getId().equals(hostUser.getId());

            PvpHistory hostHistory = PvpHistory.builder()
                    .user(hostUser)
                    .room(room)
                    .roomName(room.getRoomName())
                    .role(PvpRole.HOST)
                    .score(hostScore)
                    .level(calculateLevel(hostScore))
                    .isWinner(hostWon)
                    .opponentUser(guestUser)
                    .opponentNickname(guestUser != null ? guestUser.getNickname() : "알 수 없음")
                    .category(room.getCategory())
                    .categoryName(room.getCategory().getName())
                    .keyword(room.getKeyword())
                    .keywordName(room.getKeyword() != null ? room.getKeyword().getName() : "")
                    .finishedAt(room.getFinishedAt())
                    .build();
            pvpHistoryRepository.save(hostHistory);
        }

        // 게스트 히스토리
        if (guestUser != null) {
            int guestScore = scoreMap.getOrDefault(guestUser.getId(), 0);
            boolean guestWon = winnerUser != null && winnerUser.getId().equals(guestUser.getId());

            PvpHistory guestHistory = PvpHistory.builder()
                    .user(guestUser)
                    .room(room)
                    .roomName(room.getRoomName())
                    .role(PvpRole.GUEST)
                    .score(guestScore)
                    .level(calculateLevel(guestScore))
                    .isWinner(guestWon)
                    .opponentUser(hostUser)
                    .opponentNickname(hostUser != null ? hostUser.getNickname() : "알 수 없음")
                    .category(room.getCategory())
                    .categoryName(room.getCategory().getName())
                    .keyword(room.getKeyword())
                    .keywordName(room.getKeyword() != null ? room.getKeyword().getName() : "")
                    .finishedAt(room.getFinishedAt())
                    .build();
            pvpHistoryRepository.save(guestHistory);
        }
    }

    /**
     * score → level 변환
     * 0→0 / 1~20→1 / 21~40→2 / 41~60→3 / 61~80→4 / 81~100→5
     */
    private static int calculateLevel(int score) {
        if (score <= 0)  return 0;
        if (score <= 20) return 1;
        if (score <= 40) return 2;
        if (score <= 60) return 3;
        if (score <= 80) return 4;
        return 5;
    }

    /**
     * 커밋 후 Redis Pub/Sub 브로드캐스트
     */
    private void broadcastAfterCommit(Long roomId, PvpMessage message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagePublisher.publish(PvpChannels.getRoomChannel(roomId), message);
            }
        });
    }

    /**
     * Feedback Request용 users 배열 생성 (항상 2명)
     * - PROCESSING: sttText 사용
     * - FAILED 또는 없음: user_text = ""
     */
    private List<FeedbackRequestDto.UserAnswer> buildUserAnswers(PvpRoom room, List<PvpSubmission> submissions) {
        User host = room.getHostUser();
        User guest = room.getGuestUser();

        if (host == null || guest == null) {
            log.warn("[MQ] Feedback Request 생성 실패: host/guest null - roomId={}", room.getId());
            return List.of();
        }

        Map<Long, PvpSubmission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        return List.of(host, guest).stream()
                .map(user -> {
                    PvpSubmission s = submissionMap.get(user.getId());
                    String text = "";
                    if (s != null && s.getStatus() == PvpSubmissionStatus.PROCESSING) {
                        text = s.getSttText() != null ? s.getSttText() : "";
                    }
                    return FeedbackRequestDto.UserAnswer.builder()
                            .userId(user.getId())
                            .userText(text)
                            .build();
                })
                .toList();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private Map<String, Object> failFeedbackJson() {
        return Map.of(
                "summary", "분석 실패",
                "keywords", List.of(),
                "facts", "분석 실패",
                "understanding", "분석 실패",
                "personalizedFeedback", "분석 실패"
        );
    }
}