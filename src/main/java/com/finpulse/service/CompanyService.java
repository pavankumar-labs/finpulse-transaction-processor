package com.finpulse.service;

import com.finpulse.dto.CompanyRegistrationRequestDTO;
import com.finpulse.dto.PendingCompanyDTO;
import com.finpulse.entity.*;
import com.finpulse.event.CompanyRejectedEvent;
import com.finpulse.exception.CompanyNotFoundException;
import com.finpulse.exception.InvalidCompanyStateException;
import com.finpulse.repository.CompanyDecisionRepository;
import com.finpulse.repository.CompanyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
@Slf4j
@RequiredArgsConstructor
@Service

public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyDecisionRepository decisionRepository;
    private  final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;


    public void register(CompanyRegistrationRequestDTO requestDTO){
        Company company= Company.builder()
                .companyCode(requestDTO.getCompanyCode())
                .companyName(requestDTO.getCompanyName())
                .contactEmail(requestDTO.getContactEmail())
                .companyUrl(requestDTO.getCompanyUrl())
                .companyStatus(CompanyStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
         companyRepository.save(company);
         emailService.sendRegistrationConfirmation
                 (company.getContactEmail(), company.getCompanyName(), company.getCompanyCode());
    }

    public List<PendingCompanyDTO> getPendingCompanies(){
        return companyRepository.findByCompanyStatus(CompanyStatus.PENDING)
                .stream().map(c ->PendingCompanyDTO.builder()
                        .id(c.getId())
                        .companyCode(c.getCompanyCode())
                        .companyName(c.getCompanyName())
                        .companyUrl(c.getCompanyUrl())
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }


    @Transactional
    public void reject(Long companyId,String reason, Long rejectingAdminId){
        if (reason == null || reason.isBlank()) {
            throw new InvalidCompanyStateException("A rejection reason is required.");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("No company found with id " + companyId));

        if (company.getCompanyStatus() != CompanyStatus.PENDING) {
            throw new InvalidCompanyStateException(
                    "Company " + companyId + " is not in PENDING status. Current status: " + company.getCompanyStatus());
        }

        company.setCompanyStatus(CompanyStatus.REJECTED);
        companyRepository.save(company);
        decisionRepository.save(CompanyDecision.builder()
                .companyId(companyId)
                .adminId(rejectingAdminId)
                .reason(reason)
                .decision(DecisionType.REJECTED)
                .decidedAt(LocalDateTime.now())
                .build());

        eventPublisher.publishEvent(
                new CompanyRejectedEvent(
                        company.getContactEmail(),
                        company.getCompanyName(),
                        company.getCompanyCode()
        ));
    }

    public CompanyStatus getStatusByCode(String companyCode){
        Company company=companyRepository.findByCompanyCode(companyCode)
                .orElseThrow(()-> new CompanyNotFoundException("No company found with code " + companyCode));
        return company.getCompanyStatus();
    }

}
