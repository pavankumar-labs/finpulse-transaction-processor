package com.finpulse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatus(ProcessingStatus status);

    List<Transaction> findByFileName(String fileName);

    boolean existsByTransactionId(String transactionId);
}
