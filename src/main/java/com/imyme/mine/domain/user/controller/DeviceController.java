package com.imyme.mine.domain.user.controller;

import com.imyme.mine.domain.user.dto.RegisterDeviceRequest;
import com.imyme.mine.domain.user.dto.RegisterDeviceResponse;
import com.imyme.mine.domain.user.service.DeviceService;
import com.imyme.mine.global.common.response.ApiResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 기기 관리 컨트롤러
 * - FCM 토큰 등록 및 기기 정보 관리
 */
@Tag(name = "04. Device", description = "기기 등록/삭제, FCM 푸시 토큰 관리 API")
@Slf4j
@RestController
@RequestMapping("/users/me/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(
        summary = "기기 등록/업데이트",
        description = "FCM 푸시 토큰과 기기 정보를 등록하거나 업데이트합니다. 동일한 deviceUuid로 재등록 시 기존 정보를 업데이트합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "기기 등록/업데이트 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Validation 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping
    public ApiResponse<RegisterDeviceResponse> registerDevice(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        log.info("기기 등록 요청: userId={}, deviceUuid={}", userPrincipal.getId(), request.deviceUuid());

        RegisterDeviceResponse response = deviceService.registerDevice(userPrincipal.getId(), request);

        return ApiResponse.success(response, "기기가 등록되었습니다.");
    }

    @Operation(
        summary = "기기 삭제",
        description = "등록된 기기를 삭제합니다. (Soft Delete) FCM 푸시 알림이 더 이상 전송되지 않습니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "기기 삭제 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "기기를 찾을 수 없음 - DEVICE_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @DeleteMapping("/{deviceUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "삭제할 기기 UUID", required = true) @PathVariable String deviceUuid
    ) {
        log.info("기기 삭제 요청: userId={}, deviceUuid={}", userPrincipal.getId(), deviceUuid);

        deviceService.deleteDevice(userPrincipal.getId(), deviceUuid);

        log.info("기기 삭제 완료: deviceUuid={}", deviceUuid);
    }
}
