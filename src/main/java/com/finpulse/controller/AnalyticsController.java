package com.finpulse.controller;

import java.time.LocalDateTime;
import java.util.List;
import com.finpulse.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<TopTraderResponseDTO>>> getTopTradersByAmount(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime,
            @RequestParam(defaultValue = "10") int limit
    )
    {
        List<TopTraderResponseDTO> response=
                        analyticsService.getTopTradersByAmount(
                                startTime,
                                endTime,
                                limit);

        return ResponseEntity.ok(ApiResponse
                .success(response,"fetched top traders successfully"));
    }

    @GetMapping("/top-traders-by-transactions")
    public ResponseEntity<ApiResponse<List<TopTraderResponseDTO>>> getTopTradersByTransactions(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime,
            @RequestParam(defaultValue = "10") int limit
    )
    {
        List<TopTraderResponseDTO> response=
                analyticsService.getTopTradersByTransactions(
                        startTime,
                        endTime,
                        limit
                );

        return ResponseEntity
                .ok(ApiResponse.success(response,"fetched top traders successfully"));
    }

    @GetMapping("/status")
     public ResponseEntity<ApiResponse<List<StatusSummaryDTO>>> getStatusSummary(
        @RequestParam LocalDateTime startTime,
        @RequestParam LocalDateTime endTime) {

        List<StatusSummaryDTO> response=analyticsService
                .getStatusSummary(startTime, endTime);
          return  ResponseEntity.
                  ok(ApiResponse.success(response,"fetched status successfully"));
        }  

}
