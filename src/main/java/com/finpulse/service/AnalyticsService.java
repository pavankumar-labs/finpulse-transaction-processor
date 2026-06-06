package com.finpulse.service;

import java.time.LocalDateTime;
import java.util.List;

import com.finpulse.config.CacheConfig;
import com.finpulse.dto.StatusSummaryDTO;
import com.finpulse.dto.TopTraderResponseDTO;
import com.finpulse.repository.TransactionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class AnalyticsService {



    private final TransactionRepository repository;


    @Cacheable(
            value = CacheConfig.ANALYTICS,
            key = "'topTradersAmount_'+#startTime+'_'+#endTime"
    )
    public List<TopTraderResponseDTO> getTopTradersByAmount
    (LocalDateTime startTime,LocalDateTime endTime,int limit){

        Pageable pageable = PageRequest.of(0, limit);
        return repository.findTopTradersByAmount(startTime, endTime,pageable);
    }


    @Cacheable(
            value = CacheConfig.ANALYTICS,
            key = "'topTradersTxn_'+#startTime+'_'+#endTime"
    )
    public List<TopTraderResponseDTO> getTopTradersByTransactions
    (LocalDateTime starTime,LocalDateTime endTime,int limit){

        Pageable pageable = PageRequest.of(0, limit);
        return repository.findTopTradersByTransactions(starTime, endTime,pageable);  
    }




    @Cacheable(
            value = CacheConfig.ANALYTICS,
            key = "'statusSummary_'+#startTime+'_'+#endTime"
    )
    public List<StatusSummaryDTO> getStatusSummary(
        LocalDateTime startTime,
        LocalDateTime endTime) {

          return  repository.findStatusSummary(
                    startTime,
                    endTime
            );
    }
}