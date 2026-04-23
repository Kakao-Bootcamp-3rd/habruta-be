package com.imyme.mine.domain.notification.controller;

import com.imyme.mine.domain.notification.dto.MarkAllReadResponse;
import com.imyme.mine.domain.notification.dto.NotificationListResponse;
import com.imyme.mine.domain.notification.dto.NotificationPreferencesRequest;
import com.imyme.mine.domain.notification.dto.NotificationPreferencesResponse;
import com.imyme.mine.domain.notification.dto.UnreadCountResponse;
import com.imyme.mine.domain.notification.service.NotificationService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.UserPrincipal;
import com.imyme.mine.global.security.annotation.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10. Notification", description = "알림 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
        summary = "알림 목록 조회",
        description = "커서 기반 페이지네이션으로 알림 목록을 최신순으로 조회합니다."
    )
    @SecurityRequirement(name = "JWT")
    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications(
        @CurrentUser UserPrincipal principal,
        @Parameter(description = "읽음 필터 (true: 읽음, false: 안 읽음, 생략 시 전체)")
        @RequestParam(required = false) Boolean isRead,
        @Parameter(description = "알림 타입 필터 (LEVEL_UP, CARD_COMPLETE, PVP_MATCHED, PVP_RESULT, CHALLENGE_OPEN, CHALLENGE_RESULT, SYSTEM)")
        @RequestParam(required = false) String type,
        @Parameter(description = "마지막으로 조회한 알림의 커서 값")
        @RequestParam(required = false) String cursor,
        @Parameter(description = "페이지 크기 (기본 20, 최대 100)")
        @RequestParam(required = false) @Min(1) @Max(100) Integer size
    ) {
        NotificationListResponse response = notificationService.getNotifications(
            principal.getId(), isRead, type, cursor, size
        );
        return ApiResponse.success(response);
    }

    @Operation(
        summary = "알림 수신 설정 변경",
        description = "알림 타입별 수신 여부를 변경합니다. 설정이 없는 경우 기본값(모두 허용)으로 생성됩니다."
    )
    @SecurityRequirement(name = "JWT")
    @PutMapping("/preferences")
    public ApiResponse<NotificationPreferencesResponse> updatePreferences(
        @CurrentUser UserPrincipal principal,
        @Valid @RequestBody NotificationPreferencesRequest request
    ) {
        return ApiResponse.success(notificationService.updatePreferences(principal.getId(), request));
    }

    @Operation(
        summary = "안 읽은 알림 개수 조회",
        description = "읽지 않은 알림 개수를 조회합니다. 네비게이션 바 뱃지 표시용 초경량 API입니다."
    )
    @SecurityRequirement(name = "JWT")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(
        @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(notificationService.getUnreadCount(principal.getId()));
    }

    @Operation(
        summary = "모든 알림 읽음 처리",
        description = "읽지 않은 알림을 단일 쿼리로 일괄 읽음 처리합니다. 읽지 않은 알림이 없어도 200을 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    @PatchMapping("/read-all")
    public ApiResponse<MarkAllReadResponse> markAllAsRead(
        @CurrentUser UserPrincipal principal
    ) {
        return ApiResponse.success(notificationService.markAllAsRead(principal.getId()));
    }

    @Operation(
        summary = "알림 읽음 처리",
        description = "알림을 읽음 상태로 변경합니다. 이미 읽은 알림이어도 204를 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
        @CurrentUser UserPrincipal principal,
        @Parameter(description = "알림 ID") @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(principal.getId(), notificationId);
    }
}