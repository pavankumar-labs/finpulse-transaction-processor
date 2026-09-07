package com.finpulse.service;

import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.Admin;
import com.finpulse.entity.AdminPasswordResetToken;
import com.finpulse.event.PasswordResetRequestedEvent;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.repository.AdminPasswordResetTokenRepository;
import com.finpulse.repository.AdminRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AdminAuthSelfServiceService {

    private static final int RESET_TOKEN_EXPIRY_HOURS = 1;

    private final AdminPasswordResetTokenRepository resetTokenRepository;
    private final AdminRepository adminRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoderConfig passwordEncoderConfig;

    @Transactional
    public void changePassword(Long callerAdminId, String oldPassword, String newPassword){

        Admin admin = adminRepository.findById(callerAdminId)
                .orElseThrow(() -> new InvalidCredentialsException("Admin not found."));

        if (!(passwordEncoderConfig.passwordEncoder()).matches(oldPassword, admin.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }

        admin.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(newPassword));
        admin.setMustChangePassword(false);
        admin.setCredentialExpiresAt(null);
        adminRepository.save(admin);

    }

    public void initiatePasswordReset(String email) {
        Optional<Admin> admin = adminRepository.findByEmail(email);
        if (admin.isEmpty()) {
            return;
        }

        String rawToken = UUID.randomUUID().toString();
        String hash = hash(rawToken);

        AdminPasswordResetToken resetToken = AdminPasswordResetToken.builder()
                .adminId(admin.get().getId())
                .tokenHash(hash)
                .used(false)
                .expiresAt(LocalDateTime.now().plusHours(RESET_TOKEN_EXPIRY_HOURS))
                .createdAt(LocalDateTime.now())
                .build();

        resetTokenRepository.save(resetToken);
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(email, rawToken));
    }


    public void resetPassword(String rawToken, String newPassword) {
        String hash = hash(rawToken);

        AdminPasswordResetToken resetToken = resetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset link."));

        int rowsUpdated = resetTokenRepository.markUsedIfValid(hash, LocalDateTime.now());
        if (rowsUpdated == 0) {
            throw new InvalidTokenException("This reset link has already been used or has expired.");
        }

        Admin admin = adminRepository.findById(resetToken.getAdminId())
                .orElseThrow(() -> new InvalidTokenException("Admin not found."));

        admin.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(newPassword));
        admin.setMustChangePassword(false);
        admin.setCredentialExpiresAt(null);
        adminRepository.save(admin);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }

}
