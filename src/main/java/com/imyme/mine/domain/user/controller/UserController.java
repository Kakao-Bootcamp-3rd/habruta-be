package com.imyme.mine.domain.user.controller;

import com.imyme.mine.domain.user.dto.NicknameCheckResponse;
import com.imyme.mine.domain.user.dto.ProfileImagePresignedUrlRequest;
import com.imyme.mine.domain.user.dto.ProfileImagePresignedUrlResponse;
import com.imyme.mine.domain.user.dto.UpdateProfileRequest;
import com.imyme.mine.domain.user.dto.UserProfileResponse;
import com.imyme.mine.domain.user.service.ProfileImageService;
import com.imyme.mine.domain.user.service.UserService;
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
 * 사용자 관련 API 컨트롤러
 * - 프로필 조회/수정, 닉네임 중복 확인, 프로필 이미지 업로드용 Presigned URL 발급, 회원 탈퇴
 */
@Tag(name = "03. User", description = "프로필 조회/수정, 닉네임 검증, 프로필 이미지 업로드, 회원 탈퇴 API")
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProfileImageService profileImageService;

    @Operation(
        summary = "프로필 조회",
        description = "현재 로그인한 사용자의 프로필 정보를 조회합니다. 기본 정보, 게이미피케이션 데이터, 학습 통계를 포함합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "프로필 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음 - USER_NOT_FOUND",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getProfile(
            @CurrentUser UserPrincipal userPrincipal
    ) {
        log.info("프로필 조회 요청: userId={}", userPrincipal.getId());

        UserProfileResponse response = userService.getProfile(userPrincipal.getId());

        return ApiResponse.success(response);
    }

    @Operation(
        summary = "프로필 수정",
        description = "사용자 프로필 정보를 수정합니다. 닉네임, 프로필 이미지 URL/Key를 수정할 수 있습니다. 닉네임 변경 시 금지어 및 중복 검증을 수행합니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "프로필 수정 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Validation 실패",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "닉네임 중복 - NICKNAME_DUPLICATE",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422",
            description = "금지어 포함 - FORBIDDEN_WORD",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        log.info("프로필 수정 요청: userId={}", userPrincipal.getId());

        UserProfileResponse response = userService.updateProfile(userPrincipal.getId(), request);

        return ApiResponse.success(response, "프로필이 수정되었습니다.");
    }

    @Operation(
        summary = "닉네임 중복 확인",
        description = "닉네임 사용 가능 여부를 확인합니다. 형식 → 금지어 → 중복 순서로 검증합니다. 로그인한 사용자의 경우 본인 닉네임은 사용 가능으로 반환됩니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "닉네임 확인 완료 (available 필드로 사용 가능 여부 확인)"
        )
    })
    @GetMapping("/nickname/check")
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @Parameter(description = "확인할 닉네임", required = true)
            @RequestParam String nickname,
            @CurrentUser UserPrincipal userPrincipal
    ) {
        log.info("닉네임 중복 확인 요청: nickname={}", nickname);

        Long currentUserId = (userPrincipal != null) ? userPrincipal.getId() : null;

        NicknameCheckResponse response = userService.checkNickname(nickname, currentUserId);

        return ApiResponse.success(response, "닉네임 확인 완료");
    }

    @Operation(
        summary = "프로필 이미지 Presigned URL 발급",
        description = "프로필 이미지를 S3에 직접 업로드하기 위한 서명된 URL을 발급합니다. 서버 부하 최소화 및 Content-Type 사전 검증을 수행합니다. URL 유효기간은 5분입니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Presigned URL 발급 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "지원하지 않는 파일 형식 - INVALID_CONTENT_TYPE (허용: image/jpeg, image/png, image/heic, image/webp)",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @PostMapping("/me/profile-image/presigned-url")
    public ApiResponse<ProfileImagePresignedUrlResponse> generateProfileImagePresignedUrl(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody ProfileImagePresignedUrlRequest request
    ) {
        log.info("프로필 이미지 Presigned URL 요청: userId={}, contentType={}",
                userPrincipal.getId(), request.contentType());

        ProfileImagePresignedUrlResponse response =
                profileImageService.generatePresignedUrl(userPrincipal.getId(), request);

        return ApiResponse.success(response, "Presigned URL이 생성되었습니다.");
    }

    @Operation(
        summary = "회원 탈퇴",
        description = "사용자 계정을 탈퇴합니다. User는 Soft Delete되며, UserSession은 Hard Delete됩니다. 연결된 Device는 lastUser가 NULL로 설정됩니다.",
        security = @SecurityRequirement(name = "JWT")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "회원 탈퇴 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패 - UNAUTHORIZED, TOKEN_EXPIRED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 탈퇴한 사용자 - ALREADY_DELETED",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
        )
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawUser(@CurrentUser UserPrincipal userPrincipal) {
        log.info("회원 탈퇴 요청: userId={}", userPrincipal.getId());

        userService.withdrawUser(userPrincipal.getId());

        log.info("회원 탈퇴 완료: userId={}", userPrincipal.getId());
    }
}
