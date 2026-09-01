package com.finpulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "companies_decisions")
@Builder
public class CompanyDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false ,unique = true)
    private Long companyId;

    @Column(name = "admin_id", nullable = false )
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision",nullable = false)
    private DecisionType decision;

    @Column(name = "reason")
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

}
