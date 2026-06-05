package com.finpulse.controller;

import java.time.LocalDateTime;

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
    public ResponseEntity<FraudDetectionResponseDTO> getHighCountFraud(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
        FraudDetectionResponseDTO response=service.getHighTransactionCountAccounts(startTime,endTime);
        return ResponseEntity.ok(response);
        
    }

    @GetMapping("/high-repeative")
    public ResponseEntity<FraudDetectionResponseDTO> getHighRepeativeTransactions(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
        FraudDetectionResponseDTO response=service.getHighRepeativeTransactionAccounts(startTime, endTime);
        return ResponseEntity.ok(response);
        
    }

    @GetMapping("/high-receiving")
    public ResponseEntity<HighReceiverFraudResponseDTO> getReceivingAccounts(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
      HighReceiverFraudResponseDTO response=service.getHighReceivingAccounts(startTime, endTime);
        return ResponseEntity.ok(response);
        
    }

    @GetMapping("/high-amount")
    public ResponseEntity<FraudDetectionResponseDTO> getHighAmountAccounts(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ){
        FraudDetectionResponseDTO response=service.getHighTransactionAmountAccounts(startTime, endTime);
        return ResponseEntity.ok(response);
        
    }
        
}
