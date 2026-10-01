package com.finpulse.controller;

import com.finpulse.entity.*;
import com.finpulse.repository.FraudFindingRepository;
import com.finpulse.security.ApiKeyAuthFilter;
import com.finpulse.security.JwtAuthenticationFilter;
import com.finpulse.security.RateLimitFilter;
import com.finpulse.security.SecurityConfig;
import com.finpulse.testsupport.WithMockCompanyUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = FraudFindingController.class,
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
class FraudFindingControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FraudFindingRepository fraudFindingRepository;

    private FraudFinding pendingFinding(Long companyId) {
        return FraudFinding.builder()
                .id(1L).companyId(companyId).accountNumber("ACC-1")
                .riskLevel(RiskLevel.HIGH).status(FraudStatus.PENDING)
                .triggeredRuleCodes("VELOCITY").reason("High velocity")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void getFindings_noFileProcessingId_callsStatusOnlyQuery() throws Exception {
        when(fraudFindingRepository.findByCompanyIdAndStatus(eq(1L), eq(FraudStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(pendingFinding(1L))));

        mockMvc.perform(get("/api/v1/fraud-findings"))
                .andExpect(status().isOk());
        verify(fraudFindingRepository).findByCompanyIdAndStatus(eq(1L), eq(FraudStatus.PENDING), any());
        verify(fraudFindingRepository, never())
                .findByCompanyIdAndFileProcessingIdAndStatus(any(), any(), any(), any());
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void getFindings_withFileProcessingId_callsFileScopedQuery() throws Exception {
        when(fraudFindingRepository.findByCompanyIdAndFileProcessingIdAndStatus(
                eq(1L), eq("proc-1"), eq(FraudStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(pendingFinding(1L))));

        mockMvc.perform(get("/api/v1/fraud-findings").param("fileProcessingId", "proc-1"))
                .andExpect(status().isOk());
        verify(fraudFindingRepository)
                .findByCompanyIdAndFileProcessingIdAndStatus(eq(1L), eq("proc-1"), eq(FraudStatus.PENDING), any());
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void getFindings_noStatusParam_defaultsToPendingAndUsesPendingShape() throws Exception {
        when(fraudFindingRepository.findByCompanyIdAndStatus(eq(1L), eq(FraudStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(pendingFinding(1L))));

        mockMvc.perform(get("/api/v1/fraud-findings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reason").value("High velocity"))
                .andExpect(jsonPath("$.data.content[0].resolvedAt").doesNotExist());
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void getFindings_statusResolved_usesResolvedShape() throws Exception {
        FraudFinding resolved = FraudFinding.builder()
                .id(2L).companyId(1L).accountNumber("ACC-2")
                .riskLevel(RiskLevel.LOW).status(FraudStatus.RESOLVED)
                .resolvedAt(LocalDateTime.now())
                .build();
        when(fraudFindingRepository.findByCompanyIdAndStatus(eq(1L), eq(FraudStatus.RESOLVED), any()))
                .thenReturn(new PageImpl<>(List.of(resolved)));

        mockMvc.perform(get("/api/v1/fraud-findings").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].resolvedAt").exists())
                .andExpect(jsonPath("$.data.content[0].reason").doesNotExist());
    }

    @Test
    @WithMockCompanyUser(companyId = 42L)
    void getFindings_usesAuthenticatedPrincipalsCompanyId() throws Exception {
        when(fraudFindingRepository.findByCompanyIdAndStatus(eq(42L), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/fraud-findings"))
                .andExpect(status().isOk());
        verify(fraudFindingRepository).findByCompanyIdAndStatus(eq(42L), any(), any());
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void resolveFinding_belongsToCallersCompany_marksResolved() throws Exception {
        FraudFinding finding = pendingFinding(1L);
        when(fraudFindingRepository.findById(1L)).thenReturn(Optional.of(finding));

        mockMvc.perform(patch("/api/v1/fraud-findings/{id}/resolve", 1L))
                .andExpect(status().isOk());
        verify(fraudFindingRepository).save(argThat(f ->
                f.getStatus() == FraudStatus.RESOLVED && f.getResolvedAt() != null));
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void resolveFinding_belongsToDifferentCompany_returnsNotFound() throws Exception {
        FraudFinding finding = pendingFinding(2L); // belongs to company 2, caller is company 1
        when(fraudFindingRepository.findById(1L)).thenReturn(Optional.of(finding));

        mockMvc.perform(patch("/api/v1/fraud-findings/{id}/resolve", 1L))
                .andExpect(status().isNotFound());
        verify(fraudFindingRepository, never()).save(any());
    }

    @Test
    @WithMockCompanyUser(companyId = 1L)
    void resolveFinding_doesNotExist_returnsNotFound() throws Exception {
        when(fraudFindingRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/fraud-findings/{id}/resolve", 99L))
                .andExpect(status().isNotFound());
    }
}