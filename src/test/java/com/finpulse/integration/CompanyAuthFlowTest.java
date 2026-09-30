package com.finpulse.integration;

import com.finpulse.AbstractIntegrationTest;
import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.entity.CompanyUser;
import com.finpulse.entity.Role;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.testsupport.TestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class CompanyAuthFlowTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "CorrectHorseBattery123!";
    private String ownerEmail;

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordEncoderConfig passwordEncoderConfig;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.saveAndFlush(
                TestData.company().companyStatus(CompanyStatus.ACTIVE).build());
        CompanyUser owner = TestData.companyUser(company.getId())
                .role(Role.OWNER)
                .mustChangePassword(false)
                .passwordHash(passwordEncoderConfig.passwordEncoder().encode(RAW_PASSWORD))
                .build();

        companyUserRepository.saveAndFlush(owner);
        ownerEmail = owner.getEmail();
    }

    private MvcResult performLogin() throws Exception {
        String requestBody = """
                {"email": "%s", "password": "%s"}
                """.formatted(ownerEmail, RAW_PASSWORD);
        return mockMvc.perform(post("/api/auth/company/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void login_withValidCredentials_returnsAccessAndRefreshTokens() throws Exception {
        MvcResult result = performLogin();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.data.accessToken");
        String refreshToken = JsonPath.read(responseBody, "$.data.refreshToken");

        assertNotNull(accessToken);
        assertFalse(accessToken.isBlank());
        assertNotNull(refreshToken);
        assertFalse(refreshToken.isBlank());
    }

    @Test
    void refresh_withValidRefreshToken_rotatesTokenAndInvalidatesOldOne() throws Exception {
        String loginResponseBody = performLogin().getResponse().getContentAsString();
        String originalRefreshToken = JsonPath.read(loginResponseBody, "$.data.refreshToken");
        String refreshRequestBody = """
                {"refreshToken": "%s"}
                """.formatted(originalRefreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/company/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequestBody))
                .andExpect(status().isOk())
                .andReturn();

        String refreshResponseBody = refreshResult.getResponse().getContentAsString();
        String newRefreshToken = JsonPath.read(refreshResponseBody, "$.data.refreshToken");

        assertNotEquals(originalRefreshToken, newRefreshToken,
                "rotate() should issue a brand new refresh token, not reuse the old one");
        mockMvc.perform(post("/api/auth/company/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshToken_soItCannotBeUsedAgain() throws Exception {
        String loginResponseBody = performLogin().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(loginResponseBody, "$.data.refreshToken");
        String requestBody = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/company/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/company/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }
}