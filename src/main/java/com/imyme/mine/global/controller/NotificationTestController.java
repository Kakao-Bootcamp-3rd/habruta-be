package com.imyme.mine.global.controller;

import com.imyme.mine.domain.notification.entity.NotificationType;
import com.imyme.mine.domain.notification.service.NotificationCreatorService;
import com.imyme.mine.global.common.response.ApiResponse;
import com.imyme.mine.global.security.annotation.CurrentUser;
import com.imyme.mine.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 FCM 발송 로컬 테스트용 컨트롤러
 * - local / dev 프로파일에서만 활성화
 */
@RestController
@RequestMapping("/test/notification")
@RequiredArgsConstructor
@Profile({"local", "dev"})
public class NotificationTestController {

    private final NotificationCreatorService notificationCreatorService;

    @PostMapping("/solo")
    public ApiResponse<String> testSoloNotification(
            @CurrentUser UserPrincipal principal,
            @RequestParam(defaultValue = "1") Long cardId) {

        notificationCreatorService.create(
                principal.getId(),
                NotificationType.SOLO_RESULT,
                "학습 결과가 도착했어요!",
                "테스트 카드의 피드백을 확인해보세요.",
                cardId,
                "CARD_ATTEMPT"
        );
        return ApiResponse.success("SOLO_RESULT 알림 발송 요청 완료");
    }

    @PostMapping("/pvp")
    public ApiResponse<String> testPvpNotification(
            @CurrentUser UserPrincipal principal,
            @RequestParam(defaultValue = "1") Long roomId) {

        notificationCreatorService.create(
                principal.getId(),
                NotificationType.PVP_RESULT,
                "PvP 결과가 나왔어요!",
                "대결 결과를 확인해보세요.",
                roomId,
                "PVP_ROOM"
        );
        return ApiResponse.success("PVP_RESULT 알림 발송 요청 완료");
    }
}
