package com.finpulse.service;

import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.CompanyUser;
import com.finpulse.entity.Role;
import com.finpulse.entity.SubjectType;
import com.finpulse.event.CredentialIssuedEvent;
import com.finpulse.exception.AccessDeniedException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.repository.CompanyUserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CompanyUserManagementService {

    private final CompanyUserRepository companyUserRepository;
    private final CredentialGenerator credentialGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoderConfig passwordEncoderConfig;

    private static final int CREDENTIAL_EXPIRY_DAYS = 7;

    @Transactional
    public void createTeammate(Long callerCompanyId, Role callerRole, String email) {

        if (callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only the account owner can add new users.");
        }
        if (companyUserRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }

        String rawPassword = credentialGenerator.generateRawPassword();

        CompanyUser member = CompanyUser.builder()
                .companyId(callerCompanyId)
                .email(email)
                .passwordHash(passwordEncoderConfig.passwordEncoder().encode(rawPassword))
                .role(Role.MEMBER)
                .mustChangePassword(true)
                .credentialExpiresAt(LocalDateTime.now().plusDays(CREDENTIAL_EXPIRY_DAYS))
                .createdAt(LocalDateTime.now())
                .build();

        companyUserRepository.save(member);
        eventPublisher.publishEvent(new CredentialIssuedEvent(email, rawPassword));
    }


    @Transactional
    public void regenerateTeammateCredentials(Long callerCompanyId, Role callerRole, Long targetUserId) {

        if (callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only the account owner can regenerate credentials.");
        }

        CompanyUser target = companyUserRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found."));

        if (!target.getCompanyId().equals(callerCompanyId)) {
            throw new AccessDeniedException("Cannot manage users outside your own company.");
        }

        String rawPassword = credentialGenerator.generateRawPassword();
        target.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(rawPassword));
        target.setMustChangePassword(true);
        target.setCredentialExpiresAt(LocalDateTime.now().plusDays(CREDENTIAL_EXPIRY_DAYS));
        companyUserRepository.save(target);

        eventPublisher.publishEvent(new CredentialIssuedEvent(target.getEmail(), rawPassword));
    }
}
