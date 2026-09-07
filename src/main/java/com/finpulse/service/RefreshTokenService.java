package com.finpulse.service;


import com.finpulse.entity.RefreshToken;
import com.finpulse.entity.SubjectType;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${jwt.refresh-token-expiry-days}")
    private long refreshExpiryDays;

    private final RefreshTokenRepository refreshTokenRepository;

    public String issue(SubjectType type, Long subjectId){

        String rawToken = UUID.randomUUID().toString();

        refreshTokenRepository.save(RefreshToken.builder()
                .subjectType(type)
                .subjectId(subjectId)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(refreshExpiryDays))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build());

        return rawToken;
    }

    public RefreshToken validate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognized."));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token expired or revoked. Please log in again.");
        }
        return stored;
    }


    public String rotate(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);
        return issue(oldToken.getSubjectType(), oldToken.getSubjectId());
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
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
