package com.finpulse.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.finpulse.config.CacheConfig;
import com.finpulse.dto.*;
import com.finpulse.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(
            value = CacheConfig.FRAUD_HIGH_COUNT,
            key = "#startTime+'_'+#endTime"
    )
    public FraudDetectionResponseDTO getHighTransactionCountAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<SuspiciousAccountResponseDTO> accounts=transactionRepository
        .findHighTransactionCountAccounts(startTime, endTime, highCountThreshold);
        return FraudDetectionResponseDTO.builder()
        .accounts(accounts)
        .totalSuspiciousAccounts(accounts.size())
        .build();
    }

    @Cacheable(
            value = CacheConfig.FRAUD_DUPLICATES,
            key = "#startTime+'_'+#endTime"
    )
     public RepeatFraudResponseDTO  getHighRepeativeTransactionAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<RepeatTransferResponseDTO> accounts=transactionRepository
        .findHighRepeativeTransactionAccounts(startTime, endTime, repeatTransferThreshold);
        return RepeatFraudResponseDTO.builder()
        .accounts(accounts)
        .totalSuspiciousAccounts(accounts.size())
        .build();
    }

    @Cacheable(
            value = CacheConfig.FRAUD_HIGH_RECEIVING,
            key = "#startTime+'_'+#endTime"
    )
    public HighReceiverFraudResponseDTO getHighReceivingAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<HighReceiverAccountResponseDTO> accounts=transactionRepository
        .findHighReceivingAccounts(startTime, endTime, highReceiverThreshold);
        return HighReceiverFraudResponseDTO.builder()
        .totalSuspiciousAccounts(accounts.size())
        .accounts(accounts)
        .build();
    }


    @Cacheable(
            value = CacheConfig.FRAUD_HIGH_AMOUNT,
            key = "#startTime+'_'+#endTime" )
     public FraudDetectionResponseDTO  getHighTransactionAmountAccounts
            (LocalDateTime startTime,LocalDateTime endTime){
        List<SuspiciousAccountResponseDTO> accounts=transactionRepository
        .findHighTransactionAmountAccounts(highAmountThreshold, startTime, endTime, highAmountCountThreshold);
        return FraudDetectionResponseDTO.builder()
        .accounts(accounts)
        .totalSuspiciousAccounts(accounts.size())
        .build();
    }

}