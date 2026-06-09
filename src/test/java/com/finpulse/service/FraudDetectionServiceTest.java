package com.finpulse.service;


import com.finpulse.dto.FraudDetectionResponseDTO;
import com.finpulse.dto.SuspiciousAccountResponseDTO;
import com.finpulse.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    @Test
    void getHighTransactionCountAccounts_returnsSuspiciousAccounts() {

        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);

        List<SuspiciousAccountResponseDTO> fakeAccounts = List.of(
                SuspiciousAccountResponseDTO.builder()
                        .senderAccount("ACC001")
                        .transactionCount(15L)
                        .totalAmount(new BigDecimal("75000.00"))
                        .firstTransactionTime(start)
                        .lastTransactionTime(end)
                        .build(),
                SuspiciousAccountResponseDTO.builder()
                        .senderAccount("ACC002")
                        .transactionCount(12L)
                        .totalAmount(new BigDecimal("60000.00"))
                        .firstTransactionTime(start)
                        .lastTransactionTime(end)
                        .build()
        );

        when(transactionRepository.findHighTransactionCountAccounts(
                eq(start), eq(end), anyLong()
        )).thenReturn(fakeAccounts);


        FraudDetectionResponseDTO result =
                fraudDetectionService.getHighTransactionCountAccounts(start, end);


        assertNotNull(result);
        assertEquals(2, result.getTotalSuspiciousAccounts());
        assertEquals(2, result.getAccounts().size());
        assertNotNull(result.getGeneratedAt());
        assertEquals("ACC001", result.getAccounts().get(0).getSenderAccount());

        verify(transactionRepository, times(1))
                .findHighTransactionCountAccounts(eq(start), eq(end), anyLong());
    }

    @Test
    void getHighTransactionCountAccounts_whenNoSuspiciousAccounts_returnsEmptyList() {

        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);

        when(transactionRepository.findHighTransactionCountAccounts(
                eq(start), eq(end), anyLong()
        )).thenReturn(List.of());


        FraudDetectionResponseDTO result =
                fraudDetectionService.getHighTransactionCountAccounts(start, end);


        assertNotNull(result);
        assertEquals(0, result.getTotalSuspiciousAccounts());
        assertTrue(result.getAccounts().isEmpty());
    }
}