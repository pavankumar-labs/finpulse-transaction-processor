package com.finpulse.controller;

import com.finpulse.dto.ApiResponse;
import com.finpulse.dto.FraudFindingPendingResponseDTO;
import com.finpulse.dto.FraudFindingResolvedResponseDTO;
import com.finpulse.entity.FraudFinding;
import com.finpulse.entity.FraudStatus;
import com.finpulse.exception.FraudFindingNotFoundException;
import com.finpulse.repository.FraudFindingRepository;
import com.finpulse.security.FinPulseUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;


@RestController
@RequestMapping("/api/v1/fraud-findings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('COMPANY_OWNER', 'COMPANY_MEMBER')")
public class FraudFindingController {

    private final FraudFindingRepository fraudFindingRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<?>>> getFindings(
            @AuthenticationPrincipal FinPulseUserDetails principal,
            @RequestParam(defaultValue = "PENDING") FraudStatus status,
            @RequestParam(required = false) String fileProcessingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ){
        Long companyId = principal.getCompanyId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<FraudFinding> findings = (fileProcessingId != null)
                ? fraudFindingRepository.findByCompanyIdAndFileProcessingIdAndStatus(companyId, fileProcessingId, status, pageable)
                : fraudFindingRepository.findByCompanyIdAndStatus(companyId, status, pageable);

        Page<?> response = (status == FraudStatus.PENDING)
                ? findings.map(this::toPendingDto)
                : findings.map(this::toResolvedDto);

        return ResponseEntity.ok(ApiResponse.success(response, "Fraud findings retrieved successfully."));
    }


    private FraudFindingPendingResponseDTO toPendingDto(FraudFinding finding){

        return FraudFindingPendingResponseDTO.builder()
                .accountNumber(finding.getAccountNumber())
                .fileProcessingId(finding.getFileProcessingId())
                .riskLevel(finding.getRiskLevel())
                .triggeredRuleCodes(finding.getTriggeredRuleCodes())
                .reason(finding.getReason())
                .createdAt(finding.getCreatedAt())
                .build();
    }

    private FraudFindingResolvedResponseDTO toResolvedDto(FraudFinding finding){

        return FraudFindingResolvedResponseDTO.builder()
                .accountNumber(finding.getAccountNumber())
                .fileProcessingId(finding.getFileProcessingId())
                .riskLevel(finding.getRiskLevel())
                .resolvedAt(finding.getResolvedAt())
                .build();
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveFinding(
            @AuthenticationPrincipal FinPulseUserDetails principal, @PathVariable Long id
    ){
        Long companyId = principal.getCompanyId();

        FraudFinding finding = fraudFindingRepository.findById(id)
                .filter(f -> f.getCompanyId().equals(companyId))
                .orElseThrow(() -> new FraudFindingNotFoundException("Finding not found: " + id));

        finding.setStatus(FraudStatus.RESOLVED);
        finding.setResolvedAt(LocalDateTime.now());
        fraudFindingRepository.save(finding);

        return ResponseEntity.ok(ApiResponse.success(null, "Finding marked as resolved."));

    }
}

