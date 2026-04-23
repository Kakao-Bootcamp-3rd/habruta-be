package com.imyme.mine.domain.pvp.controller;

import com.imyme.mine.domain.pvp.dto.request.CompleteSubmissionRequest;
import com.imyme.mine.domain.pvp.dto.request.CreateRoomRequest;
import com.imyme.mine.domain.pvp.dto.request.CreateSubmissionRequest;
import com.imyme.mine.domain.pvp.dto.response.*;
import com.imyme.mine.domain.pvp.entity.PvpRoomStatus;
import com.imyme.mine.domain.pvp.messaging.PvpChannels;
import com.imyme.mine.domain.pvp.messaging.PvpMessage;
import com.imyme.mine.domain.pvp.service.PvpRoomService;

import java.util.Map;
import com.imyme.mine.domain.pvp.service.PvpRoomService.LeaveResult;
import com.imyme.mine.domain.pvp.service.PvpRoomService.LeaveType;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.messaging.MessagePublisher;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "11. PvP", description = "PvP 대결 방/기록 API")
@Slf4j
@RestController
@RequestMapping("/pvp/rooms")
@RequiredArgsConstructor
public class PvpRoomController {

    private final PvpRoomService pvpRoomService;
    private final MessagePublisher messagePublisher;

    /**
     * 4.1 방 목록 조회
     */
    @Operation(
            summary = "방 목록 조회",
            description = "PvP 대결 방 목록을 커서 페이징으로 조회합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "방 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping
    public ApiResponse<RoomListResponse> getRooms(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "카테고리 ID 필터") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "방 상태 필터", example = "OPEN") @RequestParam(defaultValue = "OPEN") PvpRoomStatus status,
            @Parameter(description = "페이지네이션 커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("방 목록 조회: userId={}, categoryId={}, status={}", principal.getId(), categoryId, status);
        return ApiResponse.success(pvpRoomService.getRooms(categoryId, status, cursor, size));
    }

    /**
     * 4.2 방 생성
     */
    @Operation(
            summary = "방 생성",
            description = "PvP 대결 방을 생성합니다. (방 이름 2~30자, 금지어 포함 시 실패)",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "방 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "금지어 포함(FORBIDDEN_WORD) / 유효성 검증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomResponse> createRoom(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request) {

        log.info("방 생성: userId={}, categoryId={}, roomName={}", principal.getId(), request.categoryId(), request.roomName());
        RoomResponse response = pvpRoomService.createRoom(principal.getId(), request);
        return ApiResponse.success(response, "방이 생성되었습니다.");
    }

    /**
     * 4.3 방 입장 (게스트)
     */
    @Operation(
            summary = "방 입장",
            description = "PvP 대결 방에 게스트로 입장합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "방 입장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "자기 방 입장 불가 - CANNOT_JOIN_OWN_ROOM",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "방 없음 - ROOM_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 매칭됨 - ROOM_ALREADY_MATCHED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "만료된 방 - ROOM_EXPIRED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping("/{roomId}/join")
    public ApiResponse<RoomResponse> joinRoom(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "방 ID", required = true) @PathVariable Long roomId) {

        log.info("방 입장: userId={}, roomId={}", principal.getId(), roomId);
        RoomResponse response = pvpRoomService.joinRoom(principal.getId(), roomId);

        // 트랜잭션 커밋 완료 후 Redis Pub/Sub으로 브로드캐스트
        String guestNickname = response.getGuest() != null ? response.getGuest().getNickname() : "게스트";
        messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                PvpMessage.guestJoined(roomId, Map.of("userId", principal.getId(), "nickname", guestNickname, "role", "GUEST"), "GUEST"));
        messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                PvpMessage.statusChange(roomId, PvpRoomStatus.MATCHED, "대결 상대가 입장했습니다."));

        return ApiResponse.success(response);
    }

    /**
     * 4.4 방 상태 조회
     */
    @Operation(
            summary = "방 상태 조회",
            description = "PvP 대결 방의 현재 상태를 조회합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "방 상태 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "참여자가 아님 - NOT_PARTICIPANT",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "방 없음 - ROOM_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "방 ID", required = true) @PathVariable Long roomId) {

        log.info("방 상태 조회: userId={}, roomId={}", principal.getId(), roomId);
        return ApiResponse.success(pvpRoomService.getRoom(principal.getId(), roomId));
    }

    /**
     * 녹음 시작 (THINKING → RECORDING 전환)
     */
    @Operation(
            summary = "녹음 시작",
            description = "생각 시간이 끝나고 녹음을 시작합니다. (THINKING → RECORDING)",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "녹음 시작 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "참여자가 아님(NOT_PARTICIPANT) / 잘못된 방 상태(INVALID_ROOM_STATUS)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "방 없음 - ROOM_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping("/{roomId}/start-recording")
    public ApiResponse<RoomResponse> startRecording(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "방 ID", required = true) @PathVariable Long roomId) {

        log.info("녹음 시작: userId={}, roomId={}", principal.getId(), roomId);
        RoomResponse response = pvpRoomService.startRecording(principal.getId(), roomId);
        return ApiResponse.success(response);
    }

    /**
     * 4.5 녹음 제출 (Presigned URL 발급)
     */
    @Operation(
            summary = "녹음 제출 URL 발급",
            description = "녹음 파일을 S3에 직접 업로드할 수 있는 Presigned URL을 발급합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Presigned URL 발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "참여자가 아님(NOT_PARTICIPANT) / 잘못된 방 상태(INVALID_ROOM_STATUS)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "방 없음 - ROOM_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 제출됨 - ALREADY_SUBMITTED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping("/{roomId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubmissionResponse> createSubmission(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "방 ID", required = true) @PathVariable Long roomId,
            @Valid @RequestBody CreateSubmissionRequest request) {

        log.info("녹음 제출 URL 발급: userId={}, roomId={}, fileName={}, fileSize={}",
                principal.getId(), roomId, request.fileName(), request.fileSize());

        SubmissionResponse response = pvpRoomService.createSubmission(
                principal.getId(), roomId, request);

        return ApiResponse.success(response, "녹음 제출 URL이 발급되었습니다.");
    }

    /**
     * 4.6 녹음 제출 완료 (분석 요청)
     */
    @Operation(
            summary = "녹음 제출 완료",
            description = "S3 업로드 완료 후 AI 분석을 요청합니다. 양쪽 모두 제출 시 분석이 시작됩니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "제출 완료 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 - FORBIDDEN",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "제출 없음 - SUBMISSION_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 제출됨 - ALREADY_SUBMITTED",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @PostMapping("/submissions/{submissionId}/complete")
    public ApiResponse<SubmissionResponse> completeSubmission(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "제출 ID", required = true) @PathVariable Long submissionId,
            @Valid @RequestBody CompleteSubmissionRequest request) {

        log.info("녹음 제출 완료: userId={}, submissionId={}, durationSeconds={}",
                principal.getId(), submissionId, request.durationSeconds());

        SubmissionResponse response = pvpRoomService.completeSubmission(
                principal.getId(), submissionId, request);

        return ApiResponse.success(response);
    }

    /**
     * 4.7 PvP 결과 조회
     */
    @Operation(
            summary = "PvP 결과 조회",
            description = "PvP 대결 결과를 조회합니다. PROCESSING 상태면 분석 중 메시지, FINISHED 상태면 전체 결과를 반환합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "결과 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "참여자가 아님(NOT_PARTICIPANT) / 잘못된 방 상태(INVALID_ROOM_STATUS)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "방 없음 - ROOM_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @GetMapping("/{roomId}/result")
    public ApiResponse<RoomResultResponse> getRoomResult(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "방 ID", required = true) @PathVariable Long roomId) {

        log.info("PvP 결과 조회: userId={}, roomId={}", principal.getId(), roomId);
        return ApiResponse.success(pvpRoomService.getRoomResult(principal.getId(), roomId));
    }

    /**
     * 4.10 방 나가기
     */
    @Operation(
            summary = "방 나가기",
            description = "PvP 대결 방에서 나갑니다. 호스트는 방을 취소하고, 게스트는 매칭을 취소합니다.",
            security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "방 나가기 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "게임 진행 중 나가기 불가(GAME_ALREADY_STARTED) / 참여자가 아님(NOT_PARTICIPANT)",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "방 없음 - ROOM_NOT_FOUND",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "방 ID", required = true) @PathVariable Long roomId) {

        log.info("방 나가기: userId={}, roomId={}", principal.getId(), roomId);
        LeaveResult result = pvpRoomService.leaveRoom(principal.getId(), roomId);

        // WS disconnect가 이미 처리한 경우 (Race condition) - 브로드캐스트 없이 204 반환
        if (result.type() == LeaveType.NOOP) {
            log.info("방 나가기 NOOP (이미 처리됨): userId={}, roomId={}", principal.getId(), roomId);
            return;
        }

        // 트랜잭션 커밋 완료 후 Redis Pub/Sub으로 브로드캐스트
        if (result.type() == LeaveType.HOST_LEFT) {
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.hostLeft(roomId));
        } else {
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.guestLeft(roomId, principal.getId(), "GUEST"));
            messagePublisher.publish(PvpChannels.getRoomChannel(roomId),
                    PvpMessage.statusChange(roomId, PvpRoomStatus.OPEN, "대결 상대를 기다리고 있습니다."));
        }
    }
}