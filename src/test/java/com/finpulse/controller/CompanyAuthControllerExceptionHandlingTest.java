package com.finpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.LoginRequestDTO;
import com.finpulse.exception.AccessDeniedException;
import com.finpulse.exception.CredentialExpiredException;
import com.finpulse.exception.InvalidCompanyStateException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.security.*;
import com.finpulse.service.CompanyAuthSelfServiceService;
import com.finpulse.service.CompanyOnboardingService;
import com.finpulse.service.CompanyUserManagementService;
import com.finpulse.service.RefreshTokenService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(
        controllers = CompanyAuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        SecurityConfig.class,
                        JwtAuthenticationFilter.class,
                        ApiKeyAuthFilter.class,
                        RateLimitFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class CompanyAuthControllerExceptionHandlingTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private CompanyOnboardingService companyOnboardingService;
    @MockitoBean
    private CompanyUserManagementService companyUserManagementService;
    @MockitoBean
    private CompanyAuthSelfServiceService companyAuthSelfServiceService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @ParameterizedTest
    @MethodSource("loginExceptionScenarios")
    void login_serviceThrows_returnsExpectedStatusAndMessage(
            RuntimeException thrownException, HttpStatus expectedStatus, String expectedResponseMessage)
            throws Exception {

        when(companyOnboardingService.verifyLogin(anyString(), anyString()))
                .thenThrow(thrownException);

        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("owner@acme.com");
        request.setPassword("wrongPassword123");

        mockMvc.perform(post("/api/auth/company/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(expectedResponseMessage))
                .andExpect(jsonPath("$.path").value("/api/auth/company/login"));
    }

    static Stream<Arguments> loginExceptionScenarios() {
        String invalidCredsMessage = "Invalid email or password.";
        String expiredCredsMessage =
                "Your temporary password has expired. Ask an administrator to resend your credentials.";
        String accessDeniedMessage = "Only the owner admin can register new admins.";
        String invalidStateMessage = "Company 1 is not in PENDING status. Current status: ACTIVE";

        return Stream.of(
                Arguments.of(
                        new InvalidCredentialsException(invalidCredsMessage),
                        HttpStatus.UNAUTHORIZED,
                        invalidCredsMessage),

                Arguments.of(
                        new CredentialExpiredException(expiredCredsMessage),
                        HttpStatus.FORBIDDEN,
                        expiredCredsMessage),

                Arguments.of(
                        new AccessDeniedException(accessDeniedMessage),
                        HttpStatus.FORBIDDEN,
                        accessDeniedMessage),

                Arguments.of(
                        new InvalidCompanyStateException(invalidStateMessage),
                        HttpStatus.CONFLICT,
                        invalidStateMessage),

                Arguments.of(
                        new RuntimeException("unexpected database timeout"),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Something went wrong. Please try again later.")
        );
    }
}