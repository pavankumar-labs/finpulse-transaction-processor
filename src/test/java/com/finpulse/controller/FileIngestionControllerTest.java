package com.finpulse.controller;

import com.finpulse.security.ApiKeyAuthFilter;
import com.finpulse.security.JwtAuthenticationFilter;
import com.finpulse.security.RateLimitFilter;
import com.finpulse.security.SecurityConfig;
import com.finpulse.service.TransactionBatchCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = FileIngestionController.class,
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
class FileIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TransactionBatchCoordinator coordinator;

    @Test
    void ingestFile_validCsv_returnsProcessingId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "transactions.csv", "text/csv", "header\nTXN1,ACC1,ACC2,100,2026-01-01T00:00:00,personal,business".getBytes());

        when(coordinator.streamFileContents(eq("transactions.csv"), any(), eq(7L)))
                .thenReturn("proc-123");

        mockMvc.perform(multipart("/api/v1/ledger/upload")
                        .file(file)
                        .requestAttr(ApiKeyAuthFilter.COMPANY_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("proc-123"));
    }

    @Test
    void ingestFile_nonCsvFilename_rejectedBeforeReachingCoordinator() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "transactions.txt", "text/plain", "not a csv".getBytes());

        mockMvc.perform(multipart("/api/v1/ledger/upload")
                        .file(file)
                        .requestAttr(ApiKeyAuthFilter.COMPANY_ID_ATTRIBUTE, 7L))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(coordinator);
    }

    @Test
    void ingestFile_coordinatorThrows_wrappedAsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "transactions.csv", "text/csv", "header\nbad-row".getBytes());

        when(coordinator.streamFileContents(anyString(), any(), anyLong()))
                .thenThrow(new RuntimeException("disk full"));

        mockMvc.perform(multipart("/api/v1/ledger/upload")
                        .file(file)
                        .requestAttr(ApiKeyAuthFilter.COMPANY_ID_ATTRIBUTE, 7L))
                .andExpect(status().isBadRequest());
    }
}