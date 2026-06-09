package com.finpulse.controller;

import java.time.LocalDateTime;
import com.finpulse.dto.ApiResponse;
import com.finpulse.dto.RepeatFraudResponseDTO;
import com.finpulse.service.FraudDetectionService;
import com.finpulse.dto.FraudDetectionResponseDTO;
import com.finpulse.dto.HighReceiverFraudResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/fraud")
@RequiredArgsConstructor
public class FraudDetectionController {

    private final FraudDetectionService service;
    
    @GetMapping("/high-count")
    public ResponseEntity<ApiResponse<FraudDetectionResponseDTO>> getHighCountFraud(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
        FraudDetectionResponseDTO response=
                service.getHighTransactionCountAccounts(startTime,endTime);
        response.setGeneratedAt(LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse
                .success(response,"detected accounts successfully"));
    }

    @GetMapping("/high-repeative")
    public ResponseEntity<ApiResponse<RepeatFraudResponseDTO>> getHighRepeativeTransactions(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
        RepeatFraudResponseDTO response=
                service.getHighRepeativeTransactionAccounts(startTime, endTime);
        response.setGeneratedAt(LocalDateTime.now());

        return ResponseEntity.
                ok(ApiResponse.success(response,"detected accounts successfully"));
    }

    @GetMapping("/high-receiving")
    public ResponseEntity<ApiResponse<HighReceiverFraudResponseDTO>> getReceivingAccounts(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
      HighReceiverFraudResponseDTO response=
              service.getHighReceivingAccounts(startTime, endTime);
        response.setGeneratedAt(LocalDateTime.now());

        return ResponseEntity.
                ok(ApiResponse.success(response,"detected accounts successfully"));
    }

    @GetMapping("/high-amount")
    public ResponseEntity<ApiResponse<FraudDetectionResponseDTO>> getHighAmountAccounts(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
        FraudDetectionResponseDTO response=
                service.getHighTransactionAmountAccounts(startTime, endTime);
        response.setGeneratedAt(LocalDateTime.now());

        return ResponseEntity.
                ok(ApiResponse.success(response,"fetched accounts successfully"));
    }
        
}
