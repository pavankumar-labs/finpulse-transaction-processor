package com.finpulse.controller;

import com.finpulse.dto.ApiResponse;
import com.finpulse.dto.PendingCompanyDTO;
import com.finpulse.dto.RejectCompanyRequestDTO;
import com.finpulse.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
public class AdminCompanyController {

    private final CompanyService companyService;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PendingCompanyDTO>>> listPending() {
        List<PendingCompanyDTO> pending = companyService.getPendingCompanies();
        return ResponseEntity.ok(ApiResponse.success(pending, "Pending companies retrieved"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long id){
        companyService.approve(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Company approved"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long id,
            @RequestBody RejectCompanyRequestDTO request) {
        companyService.reject(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "Company rejected"));
    }


}
