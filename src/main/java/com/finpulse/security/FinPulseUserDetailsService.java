package com.finpulse.security;

import com.finpulse.entity.Admin;
import com.finpulse.entity.CompanyUser;
import com.finpulse.entity.SubjectType;
import com.finpulse.repository.AdminRepository;
import com.finpulse.repository.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinPulseUserDetailsService {

    private final AdminRepository adminRepository;
    private final CompanyUserRepository companyUserRepository;

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

        String compoundAuthority = "COMPANY_" + user.getRole().name();

        return new FinPulseUserDetails(
                user.getId(), SubjectType.COMPANY_USER, user.getCompanyId(),
                user.getPasswordHash(), compoundAuthority, user.isMustChangePassword());
    }
}
