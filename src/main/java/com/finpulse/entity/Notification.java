package com.finpulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "notifications")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Column(name = "viewed", nullable = false)
    private boolean viewed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
