package com.finpulse.security;

import com.finpulse.entity.*;
import com.finpulse.repository.AdminRepository;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinPulseUserDetailsService {

    private final AdminRepository adminRepository;
    private final CompanyUserRepository companyUserRepository;
    private final CompanyRepository companyRepository;

    public FinPulseUserDetails loadBySubject(Long subjectId, SubjectType subjectType){

        if (subjectType == SubjectType.ADMIN){
            Admin admin = adminRepository.findById(subjectId)
                    .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + subjectId));

            String compoundAuthority = "ADMIN_" + admin.getRole().name();

            return new FinPulseUserDetails(
                    admin.getId(), SubjectType.ADMIN, null,
                    admin.getPasswordHash(), compoundAuthority, admin.isMustChangePassword());
        }

        CompanyUser user = companyUserRepository.findById(subjectId)
                .orElseThrow(() -> new UsernameNotFoundException("Company user not found: " + subjectId));

        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() ->
                        new DisabledException(
                                "Company account is not available."));

        if (company.getCompanyStatus() != CompanyStatus.ACTIVE) {
            throw new DisabledException(
                    "Company account is not active.");
        }

        String compoundAuthority = "COMPANY_" + user.getRole().name();

        return new FinPulseUserDetails(
                user.getId(), SubjectType.COMPANY_USER, user.getCompanyId(),
                user.getPasswordHash(), compoundAuthority, user.isMustChangePassword());
    }
}
