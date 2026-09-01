package com.finpulse.service;

import com.finpulse.dto.CompanyRegistrationRequestDTO;
import com.finpulse.dto.PendingCompanyDTO;
import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyDecision;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.entity.DecisionType;
import com.finpulse.exception.CompanyNotFoundException;
import com.finpulse.exception.InvalidCompanyStateException;
import com.finpulse.repository.CompanyDecisionRepository;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.security.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void register(CompanyRegistrationRequestDTO requestDTO){
        Company company= Company.builder()
                .companyCode(requestDTO.getCompanyCode())
                .companyName(requestDTO.getCompanyName())
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

    public void approve(Long companyId){
        Company company=companyRepository.findById(companyId)
                .orElseThrow(()->new CompanyNotFoundException("no company found with  id"+companyId));
        if(company.getCompanyStatus()!=CompanyStatus.PENDING){
            throw new InvalidCompanyStateException(
                    "Company " + companyId + " is not in PENDING status. Current status: " + company.getCompanyStatus());
        }

        String rawKey= ApiKeyGenerator.generateRawKey();
        company.setApiHashCode(ApiKeyGenerator.hash(rawKey));
        company.setCompanyStatus(CompanyStatus.ACTIVE);
        companyRepository.save(company);
        emailService.sendApprovalMail(company.getContactEmail(), company.getCompanyName(),
                company.getCompanyCode(), rawKey);

        decisionRepository.save(CompanyDecision.builder()
                .companyId(companyId)
                .adminId()
                .decision(DecisionType.APPROVED)
                .decidedAt(LocalDateTime.now())
                .build());
    }

    public void reject(Long companyId,String reason){
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
        emailService.sendRejectionEmail(company.getContactEmail(), company.getCompanyName());

        decisionRepository.save(CompanyDecision.builder()
                .companyId(companyId)
                .adminId()
                .reason(reason)
                .decision(DecisionType.REJECTED)
                .decidedAt(LocalDateTime.now())
                .build());
    }

    public CompanyStatus getStatusByCode(String companyCode){
        Company company=companyRepository.findByCompanyCode(companyCode)
                .orElseThrow(()-> new CompanyNotFoundException("No company found with code " + companyCode));
        return company.getCompanyStatus();
    }

}
