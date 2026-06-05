package com.finpulse.service;

import java.time.LocalDateTime;
import java.util.List;

import com.finpulse.dto.StatusSummaryDTO;
import com.finpulse.dto.TopTraderResponseDTO;
import com.finpulse.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository repository;

    public List<TopTraderResponseDTO> getTopTradersByAmount
    (LocalDateTime starTime,LocalDateTime endTime,int limit){

        Pageable pageable = PageRequest.of(0, limit);
        return repository.findTopTradersByAmount(starTime, endTime,pageable);  
}

    public List<TopTraderResponseDTO> getTopTradersByTransactions
    (LocalDateTime starTime,LocalDateTime endTime,int limit){

        Pageable pageable = PageRequest.of(0, limit);
        return repository.findTopTradersByTransactions(starTime, endTime,pageable);  
}


    

    public List<StatusSummaryDTO> getStatusSummary(
        LocalDateTime startTime,
        LocalDateTime endTime) {

          return  repository.findStatusSummary(
                    startTime,
                    endTime
            );

    
}
}