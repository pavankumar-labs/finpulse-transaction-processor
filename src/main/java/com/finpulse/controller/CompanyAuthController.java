package com.finpulse.controller;

import com.finpulse.dto.*;
import com.finpulse.entity.CompanyUser;
import com.finpulse.entity.Role;
import com.finpulse.entity.SubjectType;
import com.finpulse.security.FinPulseUserDetails;
import com.finpulse.security.JwtUtil;
import com.finpulse.service.CompanyAuthSelfServiceService;
import com.finpulse.service.CompanyOnboardingService;
import com.finpulse.service.CompanyUserManagementService;
import com.finpulse.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CompanyAuthController {

    private final CompanyOnboardingService companyOnboardingService;
    private final CompanyUserManagementService companyUserManagementService;
    private final CompanyAuthSelfServiceService companyAuthSelfServiceService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/api/auth/company/login")
    public ApiResponse<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        CompanyUser user = companyOnboardingService.verifyLogin(request.getEmail(), request.getPassword());

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), SubjectType.COMPANY_USER, user.getCompanyId(), user.getRole().name(), user.isMustChangePassword());
        String refreshToken = refreshTokenService.issue(SubjectType.COMPANY_USER, user.getId());

        return ApiResponse.success(
                new AuthResponseDTO(accessToken, refreshToken, user.isMustChangePassword()), "Login successful.");
    }

    @PostMapping("/api/auth/company/refresh")
    public ApiResponse<AuthResponseDTO> refresh(@RequestBody RefreshRequestDTO request) {
        var stored = refreshTokenService.validate(request.getRefreshToken());
        CompanyUser user = companyOnboardingService.getById(stored.getSubjectId());

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(), SubjectType.COMPANY_USER, user.getCompanyId(), user.getRole().name(), user.isMustChangePassword());
        String newRefreshToken = refreshTokenService.rotate(stored);

        return ApiResponse.success(
                new AuthResponseDTO(newAccessToken, newRefreshToken, user.isMustChangePassword()), "Token refreshed.");
    }


    @PostMapping("/api/auth/company/logout")
    public ApiResponse<Void> logout(@RequestBody RefreshRequestDTO request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ApiResponse.success(null, "Logged out.");
    }

    @PreAuthorize("hasAuthority('COMPANY_OWNER')")
    @PostMapping("/api/company/users")
    public ApiResponse<Void> createTeammate(@AuthenticationPrincipal FinPulseUserDetails principal,
                                            @RequestBody CreateUserRequestDTO dto) {
        companyUserManagementService.createTeammate(principal.getCompanyId(), roleOf(principal), dto.getEmail());
        return ApiResponse.success(null, "Teammate created. Credentials sent by email.");
    }

    @PreAuthorize("hasAuthority('COMPANY_OWNER')")
    @PostMapping("/api/company/users/{id}/regenerate")
    public ApiResponse<Void> regenerateTeammate(@AuthenticationPrincipal FinPulseUserDetails principal,
                                                @PathVariable Long id) {
        companyUserManagementService.regenerateTeammateCredentials(principal.getCompanyId(), roleOf(principal), id);
        return ApiResponse.success(null, "Credentials regenerated and sent by email.");
    }

    @PreAuthorize("hasAnyAuthority('COMPANY_OWNER', 'COMPANY_MEMBER')")
    @PostMapping("/api/company/users/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal FinPulseUserDetails principal,
                                            @RequestBody ChangePasswordRequestDTO dto) {

        companyAuthSelfServiceService.changePassword(principal.getSubjectId(), dto.getOldPassword(), dto.getNewPassword());
        return ApiResponse.success(null, "Password changed successfully.");
    }

    @PostMapping("/api/auth/company/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody ResetPasswordRequestDTO dto) {
        companyAuthSelfServiceService.resetPassword(dto.getToken(), dto.getNewPassword());
        return ApiResponse.success(null, "Password reset successfully.");
    }

    @PostMapping("/api/auth/company/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordRequestDTO dto) {
        companyAuthSelfServiceService.initiatePasswordReset(dto.getEmail());
        return ApiResponse.success(null, "If that email is registered, a reset link has been sent.");
    }

    private Role roleOf(FinPulseUserDetails principal) {
        return Role.valueOf(principal.getCompoundAuthorityRole());
    }
}
