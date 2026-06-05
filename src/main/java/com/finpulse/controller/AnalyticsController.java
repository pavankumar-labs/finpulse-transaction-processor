package com.finpulse.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.finpulse.service.AnalyticsService;
import com.finpulse.dto.TopTraderResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finpulse.dto.StatusSummaryDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/top-traders-by-amount")
    public ResponseEntity<List<TopTraderResponseDTO>> getTopTradersByAmount(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime,
            @RequestParam(defaultValue = "10") int limit
    ) {

        return ResponseEntity.ok(
                analyticsService.getTopTradersByAmount(
                        startTime,
                        endTime,
                        limit
                )
        );
    }

    @GetMapping("/top-traders-by-transactions")
    public ResponseEntity<List<TopTraderResponseDTO>> getTopTradersByTransactions(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime,
            @RequestParam(defaultValue = "10") int limit
    ) {

        return ResponseEntity.ok(
                analyticsService.getTopTradersByAmount(
                        startTime,
                        endTime,
                        limit
                )
        );
    }

    @GetMapping("/status")
     public ResponseEntity<List<StatusSummaryDTO>> getStatusSummary(
        LocalDateTime startTime,
        LocalDateTime endTime) {

          return  ResponseEntity.ok(analyticsService.getStatusSummary(startTime, endTime));
        }  

}
