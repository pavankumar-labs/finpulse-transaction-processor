package com.finpulse.service;


import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.CompanyPasswordResetToken;
import com.finpulse.entity.CompanyUser;
import com.finpulse.event.PasswordResetRequestedEvent;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.repository.CompanyPasswordResetTokenRepository;
import com.finpulse.repository.CompanyUserRepository;
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
public class CompanyAuthSelfServiceService {

    private static final int RESET_TOKEN_EXPIRY_HOURS = 1;
    private final CompanyPasswordResetTokenRepository resetTokenRepository;
    private final CompanyUserRepository companyUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoderConfig passwordEncoderConfig;

    public void changePassword(Long callerUserId, String oldPassword, String newPassword) {
        CompanyUser user = companyUserRepository.findById(callerUserId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found."));

        if (!passwordEncoderConfig.passwordEncoder().matches(oldPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(newPassword));
        user.setMustChangePassword(false);
        user.setCredentialExpiresAt(null);
        companyUserRepository.save(user);
    }


    public void initiatePasswordReset(String email) {
        Optional<CompanyUser> user = companyUserRepository.findByEmail(email);
        if (user.isEmpty()) {
            return; // identical outcome either way — no enumeration
        }

        String rawToken = UUID.randomUUID().toString();
        String hash = hash(rawToken);

        CompanyPasswordResetToken resetToken = CompanyPasswordResetToken.builder()
                .companyUserId(user.get().getId())
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

        CompanyPasswordResetToken resetToken = resetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset link."));

        int rowsUpdated = resetTokenRepository.markUsedIfValid(hash, LocalDateTime.now());
        if (rowsUpdated == 0) {
            throw new InvalidTokenException("This reset link has already been used or has expired.");
        }

        CompanyUser user = companyUserRepository.findById(resetToken.getCompanyUserId())
                .orElseThrow(() -> new InvalidTokenException("User not found."));

        user.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(newPassword));
        user.setMustChangePassword(false);
        user.setCredentialExpiresAt(null);
        companyUserRepository.save(user);
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
