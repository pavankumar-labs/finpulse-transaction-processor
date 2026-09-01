package com.finpulse.controller;

import com.finpulse.dto.ApiResponse;
import com.finpulse.dto.CompanyRegistrationRequestDTO;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody CompanyRegistrationRequestDTO requestDTO
            ){
        companyService.register(requestDTO);
        return ResponseEntity.ok(ApiResponse.
                success(null,"Registration submitted successfully. You will be notified once reviewed."));
    }

    @GetMapping("/status/{companyCode}")
    public ResponseEntity<ApiResponse<CompanyStatus>>  checkStatus(@PathVariable String companyCode){
        CompanyStatus status=companyService.getStatusByCode(companyCode);
        return ResponseEntity.ok(ApiResponse.success(status,"status retrieved"));
    }


}
