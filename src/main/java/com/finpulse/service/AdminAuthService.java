package com.finpulse.service;

import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.Admin;
import com.finpulse.entity.Role;
import com.finpulse.event.CredentialIssuedEvent;
import com.finpulse.exception.AccessDeniedException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.repository.AdminRepository;
import com.finpulse.security.CredentialPolicy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final int CREDENTIAL_EXPIRY_DAYS = 7;

    private final CredentialGenerator credentialGenerator;
    private final AdminRepository adminRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CredentialPolicy credentialPolicy;
    private final PasswordEncoderConfig passwordEncoderConfig;


    @Transactional
    public void registerAdmin(Role callerRole, String email){

        if (callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only the owner admin can register new admins.");
        }
        if (adminRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("An admin with this email already exists.");
        }
        String rawPassword = credentialGenerator.generateRawPassword();

        Admin admin = Admin.builder()
                .email(email)
                .passwordHash(passwordEncoderConfig.passwordEncoder().encode(rawPassword))
                .mustChangePassword(true)
                .role(Role.MEMBER)
                .credentialExpiresAt(LocalDateTime.now().plusDays(CREDENTIAL_EXPIRY_DAYS))
                .createdAt(LocalDateTime.now())
                .build();

        adminRepository.save(admin);
        eventPublisher.publishEvent(new CredentialIssuedEvent(email, rawPassword));
    }

    public Admin verifyLogin(String email, String password){
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email "));

        if (!passwordEncoderConfig.passwordEncoder().matches(password, admin.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        credentialPolicy.enforceNotExpired(admin.isMustChangePassword(), admin.getCredentialExpiresAt());
        return admin;
    }

    @Transactional
    public void regenerateCredentials(Role callerRole,Long targetAdminId){

        if (callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only the owner admin can regenerate credentials.");
        }

        Admin admin = adminRepository.findById(targetAdminId)
                .orElseThrow(() -> new InvalidCredentialsException("Admin not found."));

        String rawPassword = credentialGenerator.generateRawPassword();
        admin.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(rawPassword));
        admin.setMustChangePassword(true);
        admin.setCredentialExpiresAt(LocalDateTime.now().plusDays(CREDENTIAL_EXPIRY_DAYS));
        adminRepository.save(admin);

        eventPublisher.publishEvent(new CredentialIssuedEvent(admin.getEmail(), rawPassword));
    }

    public Admin getById(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new InvalidCredentialsException("Admin not found."));
    }



}

