package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.entity.CompanyUser;
import com.finpulse.entity.Role;
import com.finpulse.event.PasswordResetRequestedEvent;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.testsupport.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
public class CompanyPasswordResetFlowTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyUserRepository companyUserRepository;
    @Autowired
    private PasswordEncoderConfig passwordEncoderConfig;
    @Autowired
    private ApplicationEvents applicationEvents;

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private String ownerEmail;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.ACTIVE).build());
        CompanyUser owner = TestData.companyUser(company.getId())
                .role(Role.OWNER)
                .mustChangePassword(false)
                .passwordHash(passwordEncoderConfig.passwordEncoder().encode(OLD_PASSWORD))
                .build();

        companyUserRepository.saveAndFlush(owner);
        ownerEmail = owner.getEmail();
    }

    private String triggerForgotPasswordAndCaptureRawToken() throws Exception {
        String requestBody = """
                {"email": "%s"}
                """.formatted(ownerEmail);
        mockMvc.perform(post("/api/auth/company/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        return applicationEvents.stream(PasswordResetRequestedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a PasswordResetRequestedEvent to be published"))
                .getRawToken();
    }

    @Test
    void resetPassword_withValidToken_changesPasswordSoNewPasswordLogsIn() throws Exception {
        String rawToken = triggerForgotPasswordAndCaptureRawToken();
        String resetRequestBody = """
                {"token": "%s", "newPassword": "%s"}
                """.formatted(rawToken, NEW_PASSWORD);

        mockMvc.perform(post("/api/auth/company/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetRequestBody))
                .andExpect(status().isOk());

        String loginRequestBody = """
                {"email": "%s", "password": "%s"}
                """.formatted(ownerEmail, NEW_PASSWORD);

        mockMvc.perform(post("/api/auth/company/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withAlreadyUsedToken_isRejectedOnSecondAttempt() throws Exception {
        String rawToken = triggerForgotPasswordAndCaptureRawToken();
        String resetRequestBody = """
                {"token": "%s", "newPassword": "%s"}
                """.formatted(rawToken, NEW_PASSWORD);

        mockMvc.perform(post("/api/auth/company/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetRequestBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/company/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetRequestBody))
                .andExpect(status().isUnauthorized());
    }

}
