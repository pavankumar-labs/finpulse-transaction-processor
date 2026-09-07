package com.finpulse.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_findings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudFinding {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "file_processing_id", nullable = false)
    private String fileProcessingId;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "triggered_rule_codes", nullable = false, length = 500)
    private String triggeredRuleCodes;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;


}
