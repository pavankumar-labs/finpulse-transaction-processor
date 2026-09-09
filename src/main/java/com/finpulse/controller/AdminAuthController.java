package com.finpulse.controller;

import com.finpulse.dto.*;
import com.finpulse.entity.Admin;
import com.finpulse.entity.SubjectType;
import com.finpulse.exception.UnauthorizedException;
import com.finpulse.repository.AdminRepository;
import com.finpulse.security.FinPulseUserDetails;
import com.finpulse.security.JwtUtil;
import com.finpulse.service.AdminAuthSelfServiceService;
import com.finpulse.service.AdminAuthService;
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
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminAuthSelfServiceService adminAuthSelfServiceService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final AdminRepository adminRepository;

    @PostMapping("/api/auth/admin/login")
    public ApiResponse<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        Admin admin = adminAuthService.verifyLogin(request.getEmail(), request.getPassword());

        String accessToken = jwtUtil.generateAccessToken(
                admin.getId(), SubjectType.ADMIN, null, admin.getRole().name(), admin.isMustChangePassword());

        String refreshToken = refreshTokenService.issue(SubjectType.ADMIN, admin.getId());

        return ApiResponse.success(
                new AuthResponseDTO(accessToken, refreshToken, admin.isMustChangePassword()), "Login successful.");
    }

    @PostMapping("/api/auth/admin/refresh")
    public ApiResponse<AuthResponseDTO> refresh(@RequestBody RefreshRequestDTO request) {
        var stored = refreshTokenService.validate(request.getRefreshToken());

        if (stored.getSubjectType() != SubjectType.ADMIN) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        Admin admin = adminAuthService.getById(stored.getSubjectId());

        String newAccessToken = jwtUtil.generateAccessToken(
                admin.getId(), SubjectType.ADMIN, null, admin.getRole().name(), admin.isMustChangePassword());
        String newRefreshToken = refreshTokenService.rotate(stored);

        return ApiResponse.success(
                new AuthResponseDTO(newAccessToken, newRefreshToken, admin.isMustChangePassword()), "Token refreshed.");
    }

    @PostMapping("/api/auth/admin/logout")
    public ApiResponse<Void> logout(@RequestBody RefreshRequestDTO request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ApiResponse.success(null, "Logged out.");
    }


    @PreAuthorize("hasAuthority('ADMIN_OWNER')")
    @PostMapping("/api/admin/register")
    public ApiResponse<Void> registerAdmin(@AuthenticationPrincipal FinPulseUserDetails principal
            , @RequestBody RegisterAdminRequestDTO dto) {

        adminAuthService.registerAdmin(roleOf(principal), dto.getEmail());
        return ApiResponse.success(null, "Admin created. Credentials sent by email.");
    }

    @PreAuthorize("hasAuthority('ADMIN_OWNER')")
    @PostMapping("/api/admin/{id}/regenerate")
    public ApiResponse<Void> regenerate(@AuthenticationPrincipal FinPulseUserDetails principal,@PathVariable Long id) {
        adminAuthService.regenerateCredentials(roleOf(principal), id);
        return ApiResponse.success(null, "Credentials regenerated and sent by email.");
    }

    @PreAuthorize("hasAnyAuthority('ADMIN_OWNER', 'ADMIN_MEMBER')")
    @PostMapping("/api/admin/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal FinPulseUserDetails principal, @RequestBody ChangePasswordRequestDTO dto) {

        adminAuthSelfServiceService.changePassword(principal.getSubjectId(), dto.getOldPassword(), dto.getNewPassword());
        return ApiResponse.success(null, "Password changed successfully.");
    }

    @PostMapping("/api/auth/admin/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordRequestDTO dto) {
        adminAuthSelfServiceService.initiatePasswordReset(dto.getEmail());
        return ApiResponse.success(null, "If that email is registered, a reset link has been sent.");
    }

    @PostMapping("/api/auth/admin/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody ResetPasswordRequestDTO dto) {
        adminAuthSelfServiceService.resetPassword(dto.getToken(), dto.getNewPassword());
        return ApiResponse.success(null, "Password reset successfully.");
    }

    private com.finpulse.entity.Role roleOf(FinPulseUserDetails principal) {
        return com.finpulse.entity.Role.valueOf(principal.getCompoundAuthorityRole());
    }

}
