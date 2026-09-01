package com.finpulse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rejected_transactions")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "file_processing_id", nullable = false)
    private String fileProcessingId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "raw_line", nullable = false, length = 1000)
    private String rawLine;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "transaction_id")
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RejectionStatus status;

    @Column(name = "rejected_at", nullable = false)
    private LocalDateTime rejectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

}
