package com.finpulse.service;


import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.*;
import com.finpulse.event.CompanyApprovedEvent;
import com.finpulse.exception.CompanyNotFoundException;
import com.finpulse.exception.InvalidCompanyStateException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.repository.CompanyDecisionRepository;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.security.ApiKeyGenerator;
import com.finpulse.security.CredentialPolicy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyOnboardingService {

    private final CredentialPolicy credentialPolicy;
    private final CompanyRepository companyRepository;
    private final CredentialGenerator credentialGenerator;
    private final CompanyDecisionRepository decisionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyUserRepository companyUserRepository;
    private final PasswordEncoderConfig passwordEncoderConfig;

    private static final int CREDENTIAL_EXPIRY_DAYS = 7;

    public void approve(Long companyId,Long approvingAdminId){

        Company company=companyRepository.findById(companyId)
                .orElseThrow(()->new CompanyNotFoundException("no company found with  id"+companyId));
        if(company.getCompanyStatus()!= CompanyStatus.PENDING){
            throw new InvalidCompanyStateException(
                    "Company " + companyId + " is not in PENDING status. Current status: " + company.getCompanyStatus());
        }

        String rawApiKey= ApiKeyGenerator.generateRawKey();
        company.setApiHashCode(ApiKeyGenerator.hash(rawApiKey));
        company.setCompanyStatus(CompanyStatus.ACTIVE);
        companyRepository.save(company);

        String rawPassword = credentialGenerator.generateRawPassword();

        CompanyUser owner = CompanyUser.builder()
                .companyId(companyId)
                .email(company.getContactEmail())
                .passwordHash(passwordEncoderConfig.passwordEncoder().encode(rawPassword))
                .role(Role.OWNER)
                .mustChangePassword(true)
                .credentialExpiresAt(LocalDateTime.now().plusDays(CREDENTIAL_EXPIRY_DAYS))
                .createdAt(LocalDateTime.now())
                .build();

        companyUserRepository.save(owner);

        decisionRepository.save(CompanyDecision.builder()
                .companyId(companyId)
                .adminId(approvingAdminId)
                .decision(DecisionType.APPROVED)
                .decidedAt(LocalDateTime.now())
                .build());


        eventPublisher.publishEvent(new CompanyApprovedEvent(
                company.getContactEmail(), company.getCompanyName(), company.getCompanyCode(),
                rawApiKey, rawPassword));
    }

    @Transactional
    public void regenerateOwnerCredentials(Long companyId) {
        CompanyUser owner = companyUserRepository.findOwnerByCompanyId(companyId)
                .orElseThrow(() -> new InvalidCredentialsException("No owner found for this company."));
        regenerate(owner);
    }

    public CompanyUser verifyLogin(String email, String password) {
        CompanyUser user = companyUserRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoderConfig.passwordEncoder().matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        credentialPolicy.enforceNotExpired(user.isMustChangePassword(), user.getCredentialExpiresAt());
        return user;
    }


    private void regenerate(CompanyUser user){
        String rawPassword = credentialGenerator.generateRawPassword();
        user.setPasswordHash(passwordEncoderConfig.passwordEncoder().encode(rawPassword));
        user.setMustChangePassword(true);
        user.setCredentialExpiresAt(LocalDateTime.now().plusDays(CREDENTIAL_EXPIRY_DAYS));
        companyUserRepository.save(user);

        eventPublisher.publishEvent(new com.finpulse.event.CredentialIssuedEvent(
                user.getEmail(), rawPassword));
    }

    public CompanyUser getById(Long userId) {
        return companyUserRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found."));
    }
}

