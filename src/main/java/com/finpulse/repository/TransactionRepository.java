package com.finpulse.repository;


import com.finpulse.dto.HighReceiverAccountResponseDTO;
import com.finpulse.dto.StatusSummaryDTO;
import com.finpulse.dto.SuspiciousAccountResponseDTO;
import com.finpulse.dto.TopTraderResponseDTO;
import com.finpulse.entity.ProcessingStatus;
import com.finpulse.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatus(ProcessingStatus status);

    List<Transaction> findByFileName(String fileName);

    boolean existsByTransactionId(String transactionId);



    @Query("""
            SELECT new com.finpulse.dto.HighReceiverAccountResponseDTO(
                t.receiverAccount,
                COUNT(DISTINCT t.senderAccount),
                MIN(t.transactionTime),
                MAX(t.transactionTime)
            )
            from Transaction t 
            where t.transactionTime >= :startTime
            and t.transactionTime < :endTime
            group by t.receiverAccount
            having count(distinct t.senderAccount) >= :transactionCountThreshold
            """)
    List<HighReceiverAccountResponseDTO> findHighReceivingAccounts(
       @Param("startTime") LocalDateTime startTime,
       @Param("endTime") LocalDateTime endTime,
       @Param("transactionCountThreshold") long transactionCountThreshold);


       @Query("""
            select new com.finpulse.dto.SuspiciousAccountResponseDTO(
            t.senderAccount,
            count(t),
            sum(t.amount),
            min(t.transactionTime),
            max(t.transactionTime)
            ) 
            from Transaction t 
            where t.transactionTime >= :startTime
            and t.transactionTime < :endTime
            group by t.senderAccount,t.receiverAccount
            having count(t) >= :transactionCountThreshold
            """)
    List<SuspiciousAccountResponseDTO> findHighRepeativeTransactionAccounts(
       @Param("startTime") LocalDateTime startTime,
       @Param("endTime") LocalDateTime endTime,
        @Param("transactionCountThreshold") long transactionCountThreshold);


       @Query("""
            select new com.finpulse.dto.SuspiciousAccountResponseDTO(
            t.senderAccount,
            count(t),
            sum(t.amount),
            min(t.transactionTime),
            max(t.transactionTime)
            ) 
            from Transaction t 
            where t.transactionTime >= :startTime
            and t.transactionTime < :endTime and t.amount >= :amountThreshold
            group by t.senderAccount
            having count(t) >= :transactionCountThreshold
            """)
    List<SuspiciousAccountResponseDTO> findHighTransactionAmountAccounts(
        @Param("amountThreshold")  BigDecimal amountThreshold,
       @Param("startTime") LocalDateTime startTime,
       @Param("endTime") LocalDateTime endTime,
       @Param("transactionCountThreshold") long transactionCountThreshold);


       
    @Query("""
            select new com.finpulse.dto.SuspiciousAccountResponseDTO(
            t.senderAccount,
            count(t),
            sum(t.amount),
            min(t.transactionTime),
            max(t.transactionTime)
            ) 
            from Transaction t 
            where t.transactionTime >= :startTime
            and t.transactionTime < :endTime
            group by t.senderAccount
            having count(t) >= :transactionCountThreshold
            """)
    List<SuspiciousAccountResponseDTO> findHighTransactionCountAccounts(
       @Param("startTime") LocalDateTime startTime,
       @Param("endTime") LocalDateTime endTime,
       @Param("transactionCountThreshold") long transactionCountThreshold);





    @Query("""
            select new com.finpulse.dto.TopTraderResponseDTO(
                t.senderAccount,
                count(t),
                sum(t.amount)
            )  from Transaction t
              where t.transactionTime >= :startTime and
              t.transactionTime < :endTime
              group by t.senderAccount
              order by sum(t.amount) desc
            """)
    List<TopTraderResponseDTO> findTopTradersByAmount(
    @Param("startTime") LocalDateTime startTime,
    @Param("endTime") LocalDateTime endTime,
    Pageable pageable
                       );

    @Query("""
            select new com.finpulse.dto.TopTraderResponseDTO(
                t.senderAccount,
                count(t),
                sum(t.amount)
            )  from Transaction t
              where t.transactionTime >= :startTime and
              t.transactionTime < :endTime
              group by t.senderAccount
              order by count(t) desc
            """)
    List<TopTraderResponseDTO> findTopTradersByTransactions(
    @Param("startTime") LocalDateTime startTime,
    @Param("endTime") LocalDateTime endTime,
    Pageable pageable
                       );


    @Query("""
    select new com.finpulse.dto.StatusSummaryDTO(
        t.status,
        count(t)
    )
    from Transaction t
    where t.transactionTime >= :startTime
    and t.transactionTime < :endTime
    group by t.status
    """)
    List<StatusSummaryDTO> findStatusSummary(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );



    







    
    
}
