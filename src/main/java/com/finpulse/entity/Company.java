package com.finpulse.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code",nullable = false,unique = true)
    private String companyCode;

    @Column(name = "company_name",nullable = false)
    private String companyName;

    @Column(name="api_hash_code",unique = true)
    private String apiHashCode;

    @Column(name = "company_url")
    private String companyUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_status",nullable = false)
    private  CompanyStatus companyStatus;

    @Column(name = "contact_email", nullable = false, unique = true)
    private String contactEmail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
