package com.finpulse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id",nullable = false,unique = true)
    private String transactionId;

    @Column(name = "sender_account",nullable = false)
    private String senderAccount;

    @Column(name = "receiver_account",nullable = false)
    private String receiverAccount;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_time",nullable = false)
    private LocalDateTime transactionTime;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_processing_id", nullable = false)
    private String fileProcessingId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "sender_account_type", nullable = false)
    private AccountType senderAccountType;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "receiver_account_type", nullable = false)
    private AccountType receiverAccountType;

}
