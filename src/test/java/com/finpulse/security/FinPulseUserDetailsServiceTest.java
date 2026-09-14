package com.finpulse.security;

import com.finpulse.entity.*;
import com.finpulse.repository.AdminRepository;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class FinPulseUserDetailsServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private CompanyUserRepository companyUserRepository;

    @InjectMocks
    private FinPulseUserDetailsService finPulseUserDetailsService;

    @Test
    void loadBySubject_adminNotFound_throwsUsernameNotFoundException(){
        when(adminRepository.findById(1L))
                .thenReturn(Optional.empty());
        UsernameNotFoundException thrown=assertThrows(UsernameNotFoundException.class,
                ()->finPulseUserDetailsService.loadBySubject(1L, SubjectType.ADMIN));
        assertEquals("Admin not found: 1" ,thrown.getMessage());
    }

    @Test
    void loadBySubject_adminSuccess_returnsAdminUserDetails(){
        Admin fakeAdmin=Admin.builder()
                .id(1L)
                .passwordHash("hashedpassword")
                .role(Role.OWNER)
                .mustChangePassword(false)
                        .build();
        when(adminRepository.findById(1L))
                .thenReturn(Optional.of(fakeAdmin));
        FinPulseUserDetails fakeUserDetails=finPulseUserDetailsService.loadBySubject(1L,SubjectType.ADMIN);

        assertEquals(fakeAdmin.getId(),fakeUserDetails.getSubjectId());
        assertEquals(SubjectType.ADMIN,fakeUserDetails.getSubjectType());
        assertFalse(fakeUserDetails.isMustChangePassword());
        assertNull(fakeUserDetails.getCompanyId());
        assertEquals("ADMIN_OWNER", fakeUserDetails.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void loadBySubject_companyUserNotFound_throwsUsernameNotFoundException(){
        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.empty());
        UsernameNotFoundException thrown=assertThrows(UsernameNotFoundException.class,
                ()->finPulseUserDetailsService.loadBySubject(1L, SubjectType.COMPANY_USER));
        assertEquals("Company user not found: 1" ,thrown.getMessage());
    }

    @Test
    void loadBySubject_companyNotFound_throwsDisabledException(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                        .id(1L)
                .companyId(2L)
                                .build();
        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompanyUser));
        when(companyRepository.findById(fakeCompanyUser.getCompanyId()))
                .thenReturn(Optional.empty());
        DisabledException thrown=assertThrows(DisabledException.class,
                ()->finPulseUserDetailsService.loadBySubject(1L, SubjectType.COMPANY_USER));
        assertEquals("Company account is not available." ,thrown.getMessage());
    }

    @Test
    void loadBySubject_companyNotActive_throwsDisabledException(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                .id(1L)
                .companyId(2L)
                .role(Role.OWNER)
                .build();
        Company fakeCompany=Company.builder()
                        .id(2L)
                                .companyStatus(CompanyStatus.PENDING)
                                        .build();
        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompanyUser));
        when(companyRepository.findById(fakeCompanyUser.getCompanyId()))
                .thenReturn(Optional.of(fakeCompany));
        DisabledException thrown=assertThrows(DisabledException.class,
                ()->finPulseUserDetailsService.loadBySubject(1L,SubjectType.COMPANY_USER));
        assertEquals("Company account is not active.",thrown.getMessage());
    }

    @Test
    void loadBySubject_companySuccess_returnsCompanyUserDetails(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                .id(1L)
                .companyId(2L)
                .role(Role.OWNER)
                .mustChangePassword(true)
                .build();
        Company fakeCompany=Company.builder()
                .id(2L)
                .companyStatus(CompanyStatus.ACTIVE)
                .build();
        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompanyUser));
        when(companyRepository.findById(fakeCompanyUser.getCompanyId()))
                .thenReturn(Optional.of(fakeCompany));

        FinPulseUserDetails fakeUserDetails=finPulseUserDetailsService
                .loadBySubject(1L,SubjectType.COMPANY_USER);

        assertEquals(fakeCompanyUser.getId(),fakeUserDetails.getSubjectId());
        assertEquals(fakeCompany.getId(),fakeUserDetails.getCompanyId());
        assertEquals(fakeCompanyUser.getPasswordHash(),fakeUserDetails.getPassword());
        assertTrue(fakeUserDetails.isMustChangePassword());
        assertEquals("COMPANY_OWNER", fakeUserDetails.getAuthorities().iterator().next().getAuthority());
    }
}
