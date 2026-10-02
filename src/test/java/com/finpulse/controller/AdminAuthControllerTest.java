package com.finpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpulse.dto.*;
import com.finpulse.entity.Admin;
import com.finpulse.entity.Role;
import com.finpulse.entity.SubjectType;
import com.finpulse.entity.RefreshToken;
import com.finpulse.repository.AdminRepository;
import com.finpulse.security.*;
import com.finpulse.service.AdminAuthSelfServiceService;
import com.finpulse.service.AdminAuthService;
import com.finpulse.service.RefreshTokenService;
import com.finpulse.testsupport.WithMockAdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminAuthController.class,
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
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminAuthService adminAuthService;
    @MockitoBean
    private AdminAuthSelfServiceService adminAuthSelfServiceService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private AdminRepository adminRepository;

    private Admin sampleAdmin() {
        return Admin.builder()
                .id(1L).email("admin@finpulse.com").role(Role.OWNER)
                .mustChangePassword(false)
                .build();
    }

    @Test
    void login_success_returnsTokens() throws Exception {
        when(adminAuthService.verifyLogin("admin@finpulse.com", "pass"))
                .thenReturn(sampleAdmin());
        when(jwtUtil.generateAccessToken(eq(1L), eq(SubjectType.ADMIN), isNull(), eq("OWNER"), eq(false)))
                .thenReturn("access-token-123");
        when(refreshTokenService.issue(SubjectType.ADMIN, 1L)).thenReturn("refresh-token-123");
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("admin@finpulse.com");
        request.setPassword("pass");

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-123"));
    }

    @Test
    void refresh_validAdminToken_returnsNewTokens() throws Exception {
        RefreshToken stored = RefreshToken.builder()
                .subjectType(SubjectType.ADMIN).subjectId(1L).build();
        when(refreshTokenService.validate("old-refresh")).thenReturn(stored);
        when(adminAuthService.getById(1L)).thenReturn(sampleAdmin());
        when(jwtUtil.generateAccessToken(eq(1L), eq(SubjectType.ADMIN), isNull(), eq("OWNER"), eq(false)))
                .thenReturn("new-access-token");
        when(refreshTokenService.rotate(stored)).thenReturn("new-refresh-token");
        RefreshRequestDTO request = new RefreshRequestDTO();
        request.setRefreshToken("old-refresh");

        mockMvc.perform(post("/api/auth/admin/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    @WithMockAdminUser(adminId = 5L, role = "OWNER")
    void registerAdmin_callerIsOwner_invokesServiceWithOwnerRole() throws Exception {
        RegisterAdminRequestDTO request = new RegisterAdminRequestDTO();
        request.setEmail("newadmin@finpulse.com");

        mockMvc.perform(post("/api/admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(adminAuthService).registerAdmin(Role.OWNER, "newadmin@finpulse.com");
    }

    @Test
    @WithMockAdminUser(adminId = 2L, role = "MEMBER")
    void changePassword_callerIsMember_invokesServiceWithCallerId() throws Exception {
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setOldPassword("old");
        request.setNewPassword("new");

        mockMvc.perform(post("/api/admin/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(adminAuthSelfServiceService).changePassword(2L, "old", "new");
    }
}
