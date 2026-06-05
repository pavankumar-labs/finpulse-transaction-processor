
package com.finpulse.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.finpulse.repository.TransactionRepository;
import com.finpulse.dto.FraudDetectionResponseDTO;
import com.finpulse.dto.HighReceiverAccountResponseDTO;
import com.finpulse.dto.HighReceiverFraudResponseDTO;
import com.finpulse.dto.SuspiciousAccountResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    @Value("${fraud.high-amount-count-threshold}")
    private long highAmountCountThreshold;

    @Value("${fraud.high-count-threshold}")
    private long highCountThreshold;

    @Value("${fraud.high-amount-threshold}")
    private BigDecimal highAmountThreshold;

    @Value("${fraud.repeat-transfer-threshold}")
    private long repeatTransferThreshold;

    @Value("${fraud.high-receiver-threshold}")
    private long highReceiverThreshold;


    
    private final TransactionRepository transactionRepository;

    public FraudDetectionResponseDTO getHighTransactionCountAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<SuspiciousAccountResponseDTO> accounts=transactionRepository
        .findHighTransactionCountAccounts(startTime, endTime, highCountThreshold);
        return FraudDetectionResponseDTO.builder()
        .generatedAt(LocalDateTime.now())
        .accounts(accounts)
        .totalSuspiciousAccounts(accounts.size())
        .build();
    }

     public FraudDetectionResponseDTO  getHighRepeativeTransactionAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<SuspiciousAccountResponseDTO> accounts=transactionRepository
        .findHighRepeativeTransactionAccounts(startTime, endTime, repeatTransferThreshold);
        return FraudDetectionResponseDTO.builder()
        .generatedAt(LocalDateTime.now())
        .accounts(accounts)
        .totalSuspiciousAccounts(accounts.size())
        .build();
    }

    public HighReceiverFraudResponseDTO getHighReceivingAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<HighReceiverAccountResponseDTO> accounts=transactionRepository
        .findHighReceivingAccounts(startTime, endTime, highReceiverThreshold);
        return HighReceiverFraudResponseDTO.builder()
        .generatedAt(LocalDateTime.now())
        .totalSuspiciousAccounts(accounts.size())
        .accounts(accounts)
        .build();
    }

     public FraudDetectionResponseDTO  getHighTransactionAmountAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<SuspiciousAccountResponseDTO> accounts=transactionRepository
        .findHighTransactionAmountAccounts(highAmountThreshold, startTime, endTime, highAmountCountThreshold);
        return FraudDetectionResponseDTO.builder()
        .generatedAt(LocalDateTime.now())
        .accounts(accounts)
        .totalSuspiciousAccounts(accounts.size())
        .build();
    }



    




}