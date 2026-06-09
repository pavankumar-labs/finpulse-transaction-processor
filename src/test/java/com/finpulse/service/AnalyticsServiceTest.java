package com.finpulse.service;



import com.finpulse.dto.TopTraderResponseDTO;
import com.finpulse.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getTopTradersByAmount_returnsTopTraders() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);

        List<TopTraderResponseDTO> fakeTraders = List.of(
                TopTraderResponseDTO.builder()
                        .senderAccount("ACC001")
                        .transactionCount(20L)
                        .totalAmount(new BigDecimal("200000.00"))
                        .build(),
                TopTraderResponseDTO.builder()
                        .senderAccount("ACC002")
                        .transactionCount(15L)
                        .totalAmount(new BigDecimal("150000.00"))
                        .build()
        );

        when(repository.findTopTradersByAmount(
                eq(start), eq(end), any(Pageable.class)
        )).thenReturn(fakeTraders);

        List<TopTraderResponseDTO> result =
                analyticsService.getTopTradersByAmount(start, end, 10);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ACC001", result.get(0).getSenderAccount());
        assertEquals(new BigDecimal("200000.00"),
                result.get(0).getTotalAmount());

        verify(repository, times(1))
                .findTopTradersByAmount(eq(start), eq(end), any(Pageable.class));
    }

    @Test
    void getTopTradersByAmount_whenNoTraders_returnsEmptyList() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);

        when(repository.findTopTradersByAmount(
                eq(start), eq(end), any(Pageable.class)
        )).thenReturn(List.of());

        List<TopTraderResponseDTO> result =
                analyticsService.getTopTradersByAmount(start, end, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getTopTradersByTransactions_returnsCorrectList() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);

        List<TopTraderResponseDTO> fakeTraders = List.of(
                TopTraderResponseDTO.builder()
                        .senderAccount("ACC003")
                        .transactionCount(50L)
                        .totalAmount(new BigDecimal("500000.00"))
                        .build()
        );

        when(repository.findTopTradersByTransactions(
                eq(start), eq(end), any(Pageable.class)
        )).thenReturn(fakeTraders);

        List<TopTraderResponseDTO> result =
                analyticsService.getTopTradersByTransactions(start, end, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACC003", result.get(0).getSenderAccount());
        assertEquals(50L, result.get(0).getTransactionCount());
    }
}