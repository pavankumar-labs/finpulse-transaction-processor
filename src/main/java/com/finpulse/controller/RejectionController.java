package com.finpulse.controller;

import com.finpulse.dto.ApiResponse;
import com.finpulse.entity.RejectedTransaction;
import com.finpulse.entity.RejectionStatus;
import com.finpulse.repository.RejectedTransactionRepository;
import com.finpulse.security.FinPulseUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/ledger/rejections")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('COMPANY_OWNER', 'COMPANY_MEMBER')")
public class RejectionController {

    private final RejectedTransactionRepository rejectedTransactionRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RejectedTransaction>>> getRejections(
            @AuthenticationPrincipal FinPulseUserDetails principal,
            @RequestParam RejectionStatus status,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(required = false) String fileProcessingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {


        Long companyId = principal.getCompanyId();

        Pageable pageable = PageRequest.of(page, size);
        Page<RejectedTransaction> rows = rejectedTransactionRepository
                .findRejectionsOrderedByFileRecency(companyId, status.name(), from, to,fileProcessingId, pageable);

        return ResponseEntity.ok(ApiResponse.success(rows, "Rejections retrieved"));
    }
}

