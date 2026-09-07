package com.finpulse.repository;


import com.finpulse.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFileName(String fileName);
    boolean existsByTransactionId(String transactionId);




}
