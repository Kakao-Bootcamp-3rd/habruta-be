package com.imyme.mine.domain.pvp.service;

import com.imyme.mine.domain.auth.entity.User;
import com.imyme.mine.domain.auth.repository.UserRepository;
import com.imyme.mine.domain.category.entity.Category;
import com.imyme.mine.domain.category.repository.CategoryRepository;
import com.imyme.mine.domain.forbidden.entity.ForbiddenWordType;
import com.imyme.mine.domain.forbidden.service.ForbiddenWordService;
import com.imyme.mine.domain.pvp.dto.request.*;
import com.imyme.mine.domain.pvp.dto.response.*;
import com.imyme.mine.domain.pvp.entity.*;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.domain.pvp.messaging.RabbitMQMessagePublisher;
import com.imyme.mine.domain.pvp.dto.message.SttRequestDto;
import com.imyme.mine.domain.pvp.repository.PvpFeedbackRepository;
import com.imyme.mine.domain.pvp.repository.PvpHistoryRepository;
import com.imyme.mine.domain.pvp.repository.PvpRoomRepository;
import com.imyme.mine.domain.pvp.repository.PvpSubmissionRepository;
import com.imyme.mine.domain.storage.dto.PresignedUrlResponse;
import com.imyme.mine.domain.storage.service.StorageService;
import com.imyme.mine.domain.user.service.ProfileImageService;
import com.imyme.mine.global.error.BusinessException;
import com.imyme.mine.global.error.ErrorCode;
import com.imyme.mine.global.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PvpRoomService {

    private final PvpRoomRepository pvpRoomRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ForbiddenWordService forbiddenWordService;
    private final PvpSubmissionRepository pvpSubmissionRepository;
    private final PvpHistoryRepository pvpHistoryRepository;
    private final PvpFeedbackRepository pvpFeedbackRepository;
    private final StorageService storageService;
    private final PvpAsyncService pvpAsyncService;
    private final MessagePublisher messagePublisher;
    private final ProfileImageService profileImageService;
    private final RabbitMQMessagePublisher rabbitMQMessagePublisher;
    private final com.imyme.mine.domain.pvp.websocket.PvpReadyManager pvpReadyManager;

    /**
     * 4.1 방 목록 조회 (커서 페이징)
     */
    public RoomListResponse getRooms(Long categoryId, PvpRoomStatus status, String cursor, int size) {
        // 카테고리 유효성 검사
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        LocalDateTime cursorTime = null;
        Long lastId = null;

        // 커서 파싱 (예외 처리 포함)
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split("_");
                if (parts.length == 2) {
                    cursorTime = LocalDateTime.parse(parts[0]);
                    lastId = Long.parseLong(parts[1]);
                }
            } catch (Exception e) {
                log.warn("Invalid cursor format: {}", cursor, e);
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
        }

        Pageable pageable = PageRequest.of(0, size + 1);
        List<PvpRoom> rooms;

        boolean hasCursor = cursorTime != null && lastId != null;

        if (categoryId != null && hasCursor) {
            rooms = pvpRoomRepository.findRoomsByCategoryAndStatusWithCursor(
                    categoryId, status, cursorTime, lastId, pageable);
        } else if (categoryId != null) {
            rooms = pvpRoomRepository.findRoomsByCategoryAndStatus(
                    categoryId, status, pageable);
        } else if (hasCursor) {
            rooms = pvpRoomRepository.findRoomsByStatusWithCursor(
                    status, cursorTime, lastId, pageable);
        } else {
            rooms = pvpRoomRepository.findRoomsByStatus(
                    status, pageable);
        }

        boolean hasNext = rooms.size() > size;
        List<PvpRoom> pageRooms = hasNext ? rooms.subList(0, size) : rooms;

        String nextCursor = null;
        if (hasNext && !pageRooms.isEmpty()) {
            PvpRoom last = pageRooms.get(pageRooms.size() - 1);
            nextCursor = last.getCreatedAt() + "_" + last.getId();
        }

        List<RoomListResponse.RoomItem> items = pageRooms.stream()
                .map(this::toRoomItem)
                .toList();

        return RoomListResponse.builder()
                .rooms(items)
                .meta(RoomListResponse.PageMeta.builder()
                        .size(pageRooms.size())
                        .hasNext(hasNext)
                        .nextCursor(nextCursor)
                        .build())
                .build();
    }

    /**
     * 4.2 방 생성
     */
    @Transactional
    public RoomResponse createRoom(Long userId, CreateRoomRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 금지어 검증
        if (forbiddenWordService.containsForbiddenWord(request.roomName(), ForbiddenWordType.ROOM_NAME)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_WORD);
        }

        pvpRoomRepository.findByHostUserIdAndStatus(userId, PvpRoomStatus.OPEN)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_ROOM);
                });

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PvpRoom room = PvpRoom.builder()
                .category(category)
                .roomName(request.roomName())
                .hostUser(host)
                .hostNickname(host.getNickname())
                .build();

        pvpRoomRepository.save(room);
        log.info("방 생성: roomId={}, userId={}, categoryId={}", room.getId(), userId, request.categoryId());

        return toRoomResponse(room, "방이 생성되었습니다.");
    }

    /**
     * 4.3 방 입장 (게스트)
     */
    @Transactional
    public RoomResponse joinRoom(Long userId, Long roomId) {
        // 방 조회 (낙관적 락)
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 호스트 본인 체크
        if (room.isHost(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_JOIN_OWN_ROOM);
        }

        // 방 상태 검증
        if (room.getStatus() != PvpRoomStatus.OPEN) {
            if (room.getStatus() == PvpRoomStatus.CANCELED) {
                throw new BusinessException(ErrorCode.ROOM_EXPIRED);
            }
            throw new BusinessException(ErrorCode.ROOM_ALREADY_MATCHED);
        }

        // 게스트 중복 체크 (동시성 제어)
        if (room.getGuestUser() != null) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_MATCHED);
        }

        // 게스트 조회
        User guest = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 게스트 입장 (MATCHED 전환)
        room.joinGuest(guest, guest.getNickname());

        // 저장 (낙관적 락으로 동시성 제어, saveAndFlush로 즉시 flush → 예외를 여기서 처리)
        try {
            pvpRoomRepository.saveAndFlush(room);
        } catch (OptimisticLockingFailureException e) {
            // 다른 사용자가 동시에 입장하여 버전 충돌 발생
            log.warn("동시 입장 시도로 인한 충돌: roomId={}, userId={}", roomId, userId);
            throw new BusinessException(ErrorCode.ROOM_ALREADY_MATCHED);
        } catch (Exception e) {
            // 기타 예상치 못한 오류
            log.error("방 입장 중 예상치 못한 오류 발생: roomId={}, userId={}", roomId, userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        log.info("게스트 입장: roomId={}, userId={}, status=MATCHED", roomId, userId);

        // 커밋 후 3초 타이머 예약 (커밋 전 예약 시 롤백돼도 타이머가 실행될 수 있으므로 afterCommit에서 등록)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pvpAsyncService.scheduleThinkingTransition(roomId);
            }
        });

        return toRoomResponse(room, "매칭 완료! 잠시 후 키워드가 공개됩니다.");
    }

    /**
     * READY 등록 (THINKING 상태에서 준비 완료 알림)
     * - READY 등록 후 브로드캐스트
     * - 둘 다 READY면 즉시 RECORDING 전환
     */
    @Transactional
    public RoomResponse startRecording(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 참여자 확인
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        // 이미 RECORDING 이상이면 no-op
        if (room.getStatus() == PvpRoomStatus.RECORDING
                || room.getStatus() == PvpRoomStatus.PROCESSING
                || room.getStatus() == PvpRoomStatus.FINISHED
                || room.getStatus() == PvpRoomStatus.CANCELED) {
            return toRoomResponse(room, "이미 녹음이 시작되었습니다.");
        }

        // 방 상태 검증 (THINKING 상태여야 함)
        if (room.getStatus() != PvpRoomStatus.THINKING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATUS);
        }

        // READY 등록 (Redis SADD, 중복 호출 시 no-op)
        boolean isHost = room.isHost(userId);
        String role = isHost ? "HOST" : "GUEST";
        String nickname = isHost ? room.getHostNickname() : room.getGuestNickname();
        boolean isNew = pvpReadyManager.addReady(roomId, userId);

        if (!isNew) {
            log.info("READY 중복 호출 무시: roomId={}, userId={}", roomId, userId);
            return toRoomResponse(room, "이미 준비 완료되었습니다.");
        }

        // READY 브로드캐스트
        messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                PvpMessage.ready(roomId, userId, nickname, role));

        // 둘 다 READY면 즉시 RECORDING 전환
        long readyCount = pvpReadyManager.getReadyCount(roomId);
        if (readyCount >= 2) {
            room.startRecording();
            pvpRoomRepository.save(room);
            pvpReadyManager.clearReady(roomId);
            log.info("양쪽 READY → RECORDING 즉시 전환: roomId={}", roomId);

            // 커밋 후 RECORDING_TRANSITION 타이머 취소 + 브로드캐스트 + 타임아웃 예약
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pvpAsyncService.cancelTimer(roomId, PvpAsyncService.TimerType.RECORDING_TRANSITION);
                    messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                            PvpMessage.recordingStarted(roomId));
                    pvpAsyncService.scheduleRecordingTimeout(roomId);
                }
            });

            return toRoomResponse(room, "양쪽 준비 완료! 녹음을 시작합니다.");
        }

        log.info("READY 등록: roomId={}, userId={}, readyCount={}", roomId, userId, readyCount);
        return toRoomResponse(room, "준비 완료! 상대방을 기다리고 있습니다.");
    }

    /**
     * 4.4 방 상태 조회
     */
    public RoomResponse getRoom(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        return toRoomResponse(room, null);
    }

    /**
     * 4.5 녹음 제출 (Presigned URL 발급)
     */
    @Transactional
    public SubmissionResponse createSubmission(Long userId, Long roomId, CreateSubmissionRequest request) {
        // 1. 방 조회
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 참여자 검증
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        // 3. 방 상태 검증 (RECORDING 상태여야 함)
        if (room.getStatus() != PvpRoomStatus.RECORDING) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATUS);
        }

        // 4. 중복 제출 검증
        if (pvpSubmissionRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }

        // 5. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 6. PvpSubmission 엔티티 생성 및 저장 (PENDING 상태)
        PvpSubmission submission = PvpSubmission.builder()
                .room(room)
                .user(user)
                .build();

        pvpSubmissionRepository.save(submission);
        log.info("제출 레코드 생성: submissionId={}, roomId={}, userId={}, status=PENDING",
                submission.getId(), roomId, userId);

        // 7. Presigned URL 발급
        PresignedUrlResponse presignedUrl = storageService.generatePvpPresignedUrl(
                submission.getId(),
                request.fileName(),
                request.contentType(),
                request.fileSize()
        );

        // 8. audioUrl 임시 저장 (objectKey)
        submission.setAudioUrl(presignedUrl.objectKey());
        pvpSubmissionRepository.save(submission);

        log.info("Presigned URL 발급 완료: submissionId={}, objectKey={}", submission.getId(), presignedUrl.objectKey());

        // 9. SubmissionResponse 반환
        return SubmissionResponse.builder()
                .submissionId(submission.getId())
                .roomId(roomId)
                .uploadUrl(presignedUrl.uploadUrl())
                .audioUrl(presignedUrl.objectKey()) // 임시로 objectKey 반환 (업로드 완료 후 CDN URL로 변경)
                .expiresIn(300) // 5분
                .status(submission.getStatus())
                .build();
    }

    /**
     * 4.6 녹음 제출 완료 (분석 요청)
     */
    @Transactional
    public SubmissionResponse completeSubmission(Long userId, Long submissionId, CompleteSubmissionRequest request) {
        // 1. 제출 조회
        PvpSubmission submission = pvpSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        // 2. 소유자 확인
        if (!submission.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 3. 상태 검증 (이미 제출 완료된 경우)
        if (submission.getStatus() != PvpSubmissionStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }

        // 4. audioUrl(objectKey) 검증 (S3 업로드 완료 확인)
        String objectKey = submission.getAudioUrl();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_AUDIO_URL);
        }

        // 5. 제출 완료 처리 (PENDING → UPLOADED)
        submission.submit(request.durationSeconds());
        pvpSubmissionRepository.save(submission);

        // 6. RabbitMQ 큐에 STT 변환 요청 전송
        // S3 Presigned GET URL 생성 (AI 서버가 다운로드할 수 있도록)
        String audioUrl = storageService.generatePresignedGetUrl(objectKey);

        SttRequestDto sttRequest = SttRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .roomId(submission.getRoom().getId())
                .userId(userId)
                .audioUrl(audioUrl)
                .timestamp(System.currentTimeMillis())
                .build();

        // 트랜잭션 커밋 후 RabbitMQ 메시지 발행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitMQMessagePublisher.publishSttRequest(sttRequest);
                    log.info("STT 변환 요청 발행 완료: submissionId={}, roomId={}, userId={}",
                            submissionId, submission.getRoom().getId(), userId);
                } catch (Exception e) {
                    log.error("STT 변환 요청 발행 실패: submissionId={}", submissionId, e);
                    // TODO: 실패 시 재시도 로직 또는 알림 처리
                }
            }
        });

        log.info("제출 완료: submissionId={}, userId={}, status=UPLOADED", submissionId, userId);

        // 6. 양쪽 제출 확인 (UPLOADED 상태 카운트)
        long uploadedCount = pvpSubmissionRepository.countByRoomIdAndStatus(
                submission.getRoom().getId(), PvpSubmissionStatus.UPLOADED);

        Long currentRoomId = submission.getRoom().getId();
        String message;

        if (uploadedCount == 2) {
            // 7. 양쪽 모두 제출 완료 → PROCESSING 상태로 전환
            PvpRoom room = submission.getRoom();
            room.startProcessing();
            pvpRoomRepository.save(room);
            log.info("양쪽 제출 완료 → PROCESSING 전환: roomId={}", currentRoomId);

            // TODO v2: Feedback Request는 양쪽 STT가 모두 완료된 후 발행
            // - STT Response Consumer에서 양쪽 STT 완료 확인
            // - Feedback Request 발행 (양쪽 user_text 포함)
            // - AI 서버가 Feedback Response 반환
            // - Feedback Response Consumer에서 승패 결정 및 결과 저장

            // RECORDING_TIMEOUT 타이머 취소 + PROCESSING 브로드캐스트 (커밋 후 Redis Pub/Sub)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pvpAsyncService.cancelTimer(currentRoomId, PvpAsyncService.TimerType.RECORDING_TIMEOUT);
                    messagePublisher.publish(PvpChannels.getRoomChannel(currentRoomId),
                            PvpMessage.statusChange(currentRoomId, PvpRoomStatus.PROCESSING, "양쪽 모두 제출 완료! AI 분석이 시작됩니다."));
                }
            });

            message = "제출이 완료되었습니다. AI 분석 준비 중입니다.";
        } else {
            // 8. 한쪽만 제출 → 상대방에게 알림
            log.info("한쪽만 제출 완료, 상대방 대기: roomId={}, uploadedCount={}", currentRoomId, uploadedCount);

            // ANSWER_SUBMITTED 브로드캐스트 (커밋 후 Redis Pub/Sub)
            final String nickname = submission.getUser().getNickname();
            final String role = submission.getRoom().isHost(userId) ? "HOST" : "GUEST";
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagePublisher.publish(PvpChannels.getRoomChannel(currentRoomId),
                            PvpMessage.answerSubmitted(currentRoomId, userId, nickname, role));
                }
            });

            message = "상대방의 제출을 기다리고 있습니다.";
        }

        return SubmissionResponse.builder()
                .submissionId(submission.getId())
                .roomId(submission.getRoom().getId())
                .status(submission.getStatus())
                .submittedAt(submission.getSubmittedAt())
                .message(message)
                .build();
    }

    /**
     * 나가기 결과 타입
     */
    public enum LeaveType { HOST_LEFT, GUEST_LEFT, NOOP }

    public record LeaveResult(Long roomId, LeaveType type, PvpRoomStatus newStatus) {}

    /**
     * 4.10 방 나가기
     * - 비관적 락: doRecordingTransition과 직렬화하여 레이스 컨디션 방지 (Bug 1 fix)
     */
    @Transactional
    public LeaveResult leaveRoom(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetailsForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 참여자 확인
        // WebSocket disconnect가 REST leave보다 먼저 처리된 경우 이미 방에서 제거되어 있을 수 있음 (Race condition)
        // NOT_PARTICIPANT를 던지지 않고 NOOP으로 처리하여 idempotent하게 동작
        if (!room.isParticipant(userId)) {
            log.info("방 나가기: 이미 나간 상태 (NOOP) - roomId={}, userId={}", roomId, userId);
            return new LeaveResult(roomId, LeaveType.NOOP, room.getStatus());
        }

        if (room.isHost(userId)) {
            // 호스트 나가기: OPEN, MATCHED, THINKING 상태에서만 방 취소 가능
            if (room.getStatus() != PvpRoomStatus.OPEN
                    && room.getStatus() != PvpRoomStatus.MATCHED
                    && room.getStatus() != PvpRoomStatus.THINKING) {
                throw new BusinessException(ErrorCode.GAME_ALREADY_STARTED);
            }

            room.cancel();
            pvpRoomRepository.save(room);
            log.info("호스트 방 나가기: roomId={}, status=CANCELED", roomId);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pvpAsyncService.cancelAllTimers(roomId);
                }
            });
            return new LeaveResult(roomId, LeaveType.HOST_LEFT, PvpRoomStatus.CANCELED);

        } else {
            // 게스트 나가기: MATCHED 또는 THINKING에서 허용
            if (room.getStatus() != PvpRoomStatus.MATCHED
                    && room.getStatus() != PvpRoomStatus.THINKING) {
                throw new BusinessException(ErrorCode.GAME_ALREADY_STARTED);
            }

            // 게스트 제거, 방 OPEN으로 복구 (THINKING 정보도 초기화)
            room.removeGuest();
            pvpRoomRepository.save(room);
            pvpReadyManager.clearReady(roomId);
            log.info("게스트 방 나가기: roomId={}, status=OPEN", roomId);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pvpAsyncService.cancelAllTimers(roomId);
                }
            });
            return new LeaveResult(roomId, LeaveType.GUEST_LEFT, PvpRoomStatus.OPEN);
        }
    }

    /**
     * WebSocket disconnect 시 DB 정리
     * - leaveRoom에 위임하되, 이미 종료된 방이면 예외를 던지지 않고 null 반환
     */
    @Transactional
    public LeaveResult handleDisconnect(Long userId, Long roomId) {
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElse(null);

        if (room == null) {
            log.warn("disconnect 처리 실패: 방 없음 - roomId={}, userId={}", roomId, userId);
            return null;
        }

        // 이미 종료된 방이면 DB 정리 불필요
        if (room.getStatus() == PvpRoomStatus.FINISHED
                || room.getStatus() == PvpRoomStatus.CANCELED
                || room.getStatus() == PvpRoomStatus.EXPIRED) {
            log.info("disconnect 처리 스킵: 이미 종료된 방 - roomId={}, status={}", roomId, room.getStatus());
            return null;
        }

        // 참여자가 아니면 무시
        if (!room.isParticipant(userId)) {
            log.warn("disconnect 처리 스킵: 참여자 아님 - roomId={}, userId={}", roomId, userId);
            return null;
        }

        try {
            return leaveRoom(userId, roomId);
        } catch (BusinessException e) {
            log.warn("disconnect 처리 중 예외 (무시): roomId={}, userId={}, error={}", roomId, userId, e.getMessage());
            return null;
        }
    }

    /**
     * 4.7 PvP 결과 조회 (캐싱 적용)
     * - TTL: 7일 (RedisConfig에서 설정)
     * - Immutable 데이터: 생성 후 절대 변경 없음
     * - 조건부 캐싱: FINISHED 상태일 때만 캐싱 (PROCESSING은 캐싱 안 함)
     */
    @Cacheable(value = "ai:feedback:pvp", key = "#roomId + ':' + #userId",
               condition = "#result != null && #result.status().name() == 'FINISHED'")
    public RoomResultResponse getRoomResult(Long userId, Long roomId) {
        // 1. 방 조회
        PvpRoom room = pvpRoomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 참여자 확인
        if (!room.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
        }

        // 3. PROCESSING 상태 처리
        if (room.getStatus() == PvpRoomStatus.PROCESSING) {
            return RoomResultResponse.builder()
                    .room(RoomResultResponse.RoomInfo.builder()
                            .id(room.getId())
                            .name(room.getRoomName())
                            .build())
                    .status(PvpRoomStatus.PROCESSING)
                    .message("AI 분석 중입니다.")
                    .build();
        }

        // 4. FINISHED 상태 확인
        if (room.getStatus() != PvpRoomStatus.FINISHED) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATUS);
        }

        // 5. 상대방 userId 파악 (NPE 방지)
        User opponentUser = room.isHost(userId) ? room.getGuestUser() : room.getHostUser();
        if (opponentUser == null) {
            log.error("결과 조회 시 상대방 정보 없음: roomId={}, userId={}", roomId, userId);
            throw new BusinessException(ErrorCode.INVALID_ROOM_STATUS);
        }
        Long opponentUserId = opponentUser.getId();

        // 6. 내 결과 조회
        PvpHistory myHistory = pvpHistoryRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        PvpFeedback myFeedback = pvpFeedbackRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        PvpSubmission mySubmission = pvpSubmissionRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        // 7. 상대방 결과 조회
        PvpHistory opponentHistory = pvpHistoryRepository.findByRoomIdAndUserId(roomId, opponentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        PvpFeedback opponentFeedback = pvpFeedbackRepository.findByRoomIdAndUserId(roomId, opponentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        PvpSubmission opponentSubmission = pvpSubmissionRepository.findByRoomIdAndUserId(roomId, opponentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));

        // 8. 응답 생성
        User winner = room.getWinnerUser();

        return RoomResultResponse.builder()
                .room(RoomResultResponse.RoomInfo.builder()
                        .id(room.getId())
                        .name(room.getRoomName())
                        .build())
                .category(RoomResultResponse.CategoryInfo.builder()
                        .id(room.getCategory().getId())
                        .name(room.getCategory().getName())
                        .build())
                .keyword(RoomResultResponse.KeywordInfo.builder()
                        .id(room.getKeyword().getId())
                        .name(room.getKeyword().getName())
                        .build())
                .status(PvpRoomStatus.FINISHED)
                .myResult(buildPlayerResult(myHistory, myFeedback, mySubmission, true))
                .opponentResult(buildPlayerResult(opponentHistory, opponentFeedback, opponentSubmission, false))
                .winner(winner != null ? RoomResultResponse.UserInfo.builder()
                        .id(winner.getId())
                        .nickname(winner.getNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                winner.getProfileImageKey(), winner.getProfileImageUrl()))
                        .level(winner.getLevel())
                        .build() : null)
                .finishedAt(room.getFinishedAt())
                .build();
    }

    private RoomResultResponse.PlayerResult buildPlayerResult(
            PvpHistory history,
            PvpFeedback feedback,
            PvpSubmission submission,
            boolean includeHistoryInfo) {

        User user = history.getUser();

        // FeedbackDetail 파싱
        RoomResultResponse.FeedbackDetail feedbackDetail = parseFeedbackJson(feedback.getPvpFeedbackJson());

        RoomResultResponse.PlayerResult.PlayerResultBuilder builder = RoomResultResponse.PlayerResult.builder()
                .user(RoomResultResponse.UserInfo.builder()
                        .id(user.getId())
                        .nickname(user.getNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                user.getProfileImageKey(), user.getProfileImageUrl()))
                        .level(user.getLevel())
                        .build())
                .score(feedback.getScore())
                .audioUrl(submission.getAudioUrl())
                .durationSeconds(submission.getDurationSeconds())
                .sttText(submission.getSttText())
                .feedback(feedbackDetail);

        // myResult만 historyId, isHidden 포함
        if (includeHistoryInfo) {
            builder.historyId(history.getId())
                    .isHidden(history.getIsHidden());
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private RoomResultResponse.FeedbackDetail parseFeedbackJson(Object feedbackJson) {
        if (feedbackJson instanceof java.util.Map) {
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) feedbackJson;

            // keywords는 List<String>로 변환
            java.util.List<String> keywords = null;
            Object keywordsObj = map.get("keywords");
            if (keywordsObj instanceof java.util.List) {
                keywords = (java.util.List<String>) keywordsObj;
            }

            return RoomResultResponse.FeedbackDetail.builder()
                    .summary((String) map.get("summary"))
                    .keywords(keywords)
                    .facts((String) map.get("facts"))
                    .understanding((String) map.get("understanding"))
                    .personalizedFeedback((String) map.get("personalizedFeedback"))
                    .build();
        }
        return null;
    }

    /**
     * 4.8 내 PvP 기록 조회
     */
    public MyRoomsResponse getMyHistories(
            Long userId,
            Long categoryId,
            Long keywordId,
            boolean includeHidden,
            String sort,
            String cursor,
            int size) {

        // 1. 커서 파싱
        LocalDateTime cursorFinishedAt = null;
        Integer cursorScore = null;
        Long lastId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split("_");
                if (parts.length == 2) {
                    lastId = Long.parseLong(parts[1]);
                    if ("score".equals(sort)) {
                        cursorScore = Integer.parseInt(parts[0]);
                    } else {
                        cursorFinishedAt = LocalDateTime.parse(parts[0]);
                    }
                }
            } catch (Exception e) {
                log.warn("Invalid cursor format: {}", cursor, e);
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
        }

        // 2. Repository 조회
        Pageable pageable = PageRequest.of(0, size + 1);
        List<PvpHistory> histories;

        if ("score".equals(sort)) {
            histories = pvpHistoryRepository.findMyHistoriesByScore(
                    userId, includeHidden, categoryId, keywordId, cursorScore, lastId, pageable);
        } else if ("finishedAt".equals(sort) || sort == null) {
            histories = pvpHistoryRepository.findMyHistories(
                    userId, includeHidden, categoryId, keywordId, cursorFinishedAt, lastId, pageable);
        } else {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }

        // 3. hasNext 판단
        boolean hasNext = histories.size() > size;
        List<PvpHistory> pageHistories = hasNext ? histories.subList(0, size) : histories;

        // 4. nextCursor 생성
        String nextCursor = null;
        if (hasNext && !pageHistories.isEmpty()) {
            PvpHistory last = pageHistories.get(pageHistories.size() - 1);
            if ("score".equals(sort)) {
                nextCursor = last.getScore() + "_" + last.getId();
            } else {
                nextCursor = last.getFinishedAt() + "_" + last.getId();
            }
        }

        // 5. 상대방 점수 일괄 조회 (N+1 방지)
        List<Long> roomIds = pageHistories.stream()
                .map(h -> h.getRoom().getId())
                .toList();

        // roomId -> userId -> score 매핑
        java.util.Map<Long, java.util.Map<Long, Integer>> opponentScoreMap = new java.util.HashMap<>();
        if (!roomIds.isEmpty()) {
            List<PvpHistory> allHistories = pvpHistoryRepository.findByRoomIdIn(roomIds);
            for (PvpHistory h : allHistories) {
                opponentScoreMap
                        .computeIfAbsent(h.getRoom().getId(), k -> new java.util.HashMap<>())
                        .put(h.getUser().getId(), h.getScore());
            }
        }

        // 6. 응답 생성
        List<MyRoomsResponse.HistoryItem> items = pageHistories.stream()
                .map(history -> toHistoryItem(history, opponentScoreMap))
                .toList();

        return MyRoomsResponse.builder()
                .histories(items)
                .meta(MyRoomsResponse.PageMeta.builder()
                        .size(size)
                        .hasNext(hasNext)
                        .nextCursor(nextCursor)
                        .build())
                .build();
    }

    private MyRoomsResponse.HistoryItem toHistoryItem(
            PvpHistory history,
            java.util.Map<Long, java.util.Map<Long, Integer>> opponentScoreMap) {
        // 상대방 정보 조회
        User opponent = history.getOpponentUser();
        Long opponentUserId = opponent.getId();
        Long roomId = history.getRoom().getId();

        // Map에서 상대방 점수 조회 (N+1 방지)
        Integer opponentScore = opponentScoreMap
                .getOrDefault(roomId, java.util.Collections.emptyMap())
                .get(opponentUserId);

        return MyRoomsResponse.HistoryItem.builder()
                .historyId(history.getId())
                .room(MyRoomsResponse.RoomInfo.builder()
                        .id(history.getRoom().getId())
                        .name(history.getRoomName())
                        .build())
                .category(MyRoomsResponse.CategoryInfo.builder()
                        .id(history.getCategory().getId())
                        .name(history.getCategoryName())
                        .build())
                .keyword(MyRoomsResponse.KeywordInfo.builder()
                        .id(history.getKeyword().getId())
                        .name(history.getKeywordName())
                        .build())
                .myRole(history.getRole())
                .myResult(MyRoomsResponse.MyResult.builder()
                        .score(history.getScore())
                        .level(history.getLevel())
                        .isWinner(history.getIsWinner())
                        .build())
                .opponent(MyRoomsResponse.OpponentInfo.builder()
                        .id(opponent.getId())
                        .nickname(history.getOpponentNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                opponent.getProfileImageKey(), opponent.getProfileImageUrl()))
                        .level(opponent.getLevel())
                        .score(opponentScore)
                        .build())
                .isHidden(history.getIsHidden())
                .finishedAt(history.getFinishedAt())
                .build();
    }

    /**
     * 4.9 방 숨기기
     */
    @Transactional
    public UpdateHistoryResponse updateHistoryVisibility(Long userId, Long historyId, UpdateHistoryRequest request) {
        // 1. 기록 조회
        PvpHistory history = pvpHistoryRepository.findById(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 2. 소유자 확인
        if (!history.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 3. 숨김 상태 업데이트
        history.updateHiddenStatus(request.isHidden());
        pvpHistoryRepository.save(history);

        log.info("기록 숨김 상태 변경: historyId={}, userId={}, isHidden={}", historyId, userId, request.isHidden());

        return UpdateHistoryResponse.builder()
                .historyId(history.getId())
                .isHidden(history.getIsHidden())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ===== 내부 변환 메서드 =====

    RoomResponse toRoomResponse(PvpRoom room, String message) {
        User host = room.getHostUser();
        User guest = room.getGuestUser();

        RoomResponse.KeywordInfo keywordInfo = null;
        if (room.getKeyword() != null) {
            keywordInfo = RoomResponse.KeywordInfo.builder()
                    .id(room.getKeyword().getId())
                    .name(room.getKeyword().getName())
                    .build();
        }

        // THINKING 상태일 때 생각 종료 시간 계산 (시작 시간 + 30초)
        LocalDateTime thinkingEndsAt = null;
        if (room.getStatus() == PvpRoomStatus.THINKING && room.getStartedAt() != null) {
            thinkingEndsAt = room.getStartedAt().plusSeconds(30);
        }

        return RoomResponse.builder()
                .room(RoomResponse.RoomInfo.builder()
                        .id(room.getId())
                        .name(room.getRoomName())
                        .build())
                .category(RoomResponse.CategoryInfo.builder()
                        .id(room.getCategory().getId())
                        .name(room.getCategory().getName())
                        .build())
                .status(room.getStatus())
                .keyword(keywordInfo)
                .host(RoomResponse.UserInfo.builder()
                        .id(host.getId())
                        .nickname(host.getNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                host.getProfileImageKey(), host.getProfileImageUrl()))
                        .level(host.getLevel())
                        .build())
                .guest(guest != null ? RoomResponse.UserInfo.builder()
                        .id(guest.getId())
                        .nickname(guest.getNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                guest.getProfileImageKey(), guest.getProfileImageUrl()))
                        .level(guest.getLevel())
                        .build() : null)
                .createdAt(room.getCreatedAt())
                .matchedAt(room.getMatchedAt())
                .startedAt(room.getStartedAt())
                .thinkingEndsAt(thinkingEndsAt)
                .message(message)
                .build();
    }

    private RoomListResponse.RoomItem toRoomItem(PvpRoom room) {
        User host = room.getHostUser();
        User guest = room.getGuestUser();

        return RoomListResponse.RoomItem.builder()
                .room(RoomListResponse.RoomInfo.builder()
                        .id(room.getId())
                        .name(room.getRoomName())
                        .build())
                .category(RoomListResponse.CategoryInfo.builder()
                        .id(room.getCategory().getId())
                        .name(room.getCategory().getName())
                        .build())
                .status(room.getStatus())
                .host(RoomListResponse.UserInfo.builder()
                        .id(host.getId())
                        .nickname(host.getNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                host.getProfileImageKey(), host.getProfileImageUrl()))
                        .level(host.getLevel())
                        .build())
                .guest(guest != null ? RoomListResponse.UserInfo.builder()
                        .id(guest.getId())
                        .nickname(guest.getNickname())
                        .profileImageUrl(profileImageService.resolveProfileImageUrl(
                                guest.getProfileImageKey(), guest.getProfileImageUrl()))
                        .level(guest.getLevel())
                        .build() : null)
                .createdAt(room.getCreatedAt())
                .build();
    }
}