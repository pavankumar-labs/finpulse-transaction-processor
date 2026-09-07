package com.finpulse.repository;

import com.finpulse.dto.*;
import com.finpulse.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {


    List<Transaction> findByFileProcessingId(String fileName);

    boolean existsByTransactionId(String transactionId);

    @Query("""
select t from Transactions t
where t.companyId= :companyId
and t.senderAccount in :accounts
and t.transactionTime >= :windowStart
""")
    List<Transaction> findSenderHistory(
            @Param("companyId") Long companyId,
            @Param(("accounts")) Collection<String> accounts,
            @Param("windowStart") LocalDateTime windowStart
            );

    @Query("""
select t from Transactions t
where t.companyId= :companyId
and t.receiverAccount in :accounts
and t.transactionTime >= :windowStart
""")
    List<Transaction> findReceiverHistory(
            @Param("companyId") Long companyId,
            @Param(("accounts")) Collection<String> accounts,
            @Param("windowStart") LocalDateTime windowStart
    );

}
