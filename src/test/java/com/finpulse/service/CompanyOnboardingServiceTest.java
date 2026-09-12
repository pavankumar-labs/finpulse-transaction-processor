package com.finpulse.service;


import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.dto.AuthenticatedCompanyUser;
import com.finpulse.entity.*;
import com.finpulse.event.CompanyApprovedEvent;
import com.finpulse.exception.CompanyNotFoundException;
import com.finpulse.exception.CredentialExpiredException;
import com.finpulse.exception.InvalidCompanyStateException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.repository.CompanyDecisionRepository;
import com.finpulse.repository.CompanyRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.security.ApiKeyGenerator;
import com.finpulse.security.CredentialPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompanyOnboardingServiceTest {

    @Mock
    private CompanyUserRepository companyUserRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private CredentialGenerator credentialGenerator;
    @Mock private CompanyDecisionRepository decisionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CredentialPolicy credentialPolicy;
    @Mock private PasswordEncoderConfig passwordEncoderConfig;

    @InjectMocks
    private CompanyOnboardingService companyOnboardingService;

    @Test
    void verifyLogin_wrongPassword_throwsInvalidCredentials(){

        CompanyUser fakeUser = CompanyUser.builder()
                .companyId(1L)
                .email("owner@acme.com")
                .passwordHash("someHashedValue")
                .mustChangePassword(false)
                .build();

        Company fakeCompany = Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.ACTIVE)
                .build();

        when(companyUserRepository.findByEmail("owner@acme.com"))
                .thenReturn(Optional.of(fakeUser));

        when(companyRepository.findById(fakeUser.getCompanyId()))
                .thenReturn(Optional.of(fakeCompany));

        BCryptPasswordEncoder fakeEncoder=mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.matches(anyString(),anyString())).thenReturn(false);

        InvalidCredentialsException thrown=assertThrows(
                InvalidCredentialsException.class,
                () -> companyOnboardingService.verifyLogin("owner@acme.com", "wrongPassword123")
        );

        assertEquals("Invalid email or password.", thrown.getMessage());
    }

    @Test
    void verifyLogin_userNotFound_throwsInvalidCredentials(){

        when(companyUserRepository.findByEmail("owner@acme.com"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException thrown=assertThrows(
                InvalidCredentialsException.class,
                () -> companyOnboardingService.verifyLogin("owner@acme.com", "Password123")
        );
        assertEquals("Invalid email or password.", thrown.getMessage());
    }

    @Test
    void verifyLogin_companyNotActive_throwsInvalidCredentials(){

        CompanyUser fakeUser = CompanyUser.builder()
                .companyId(1L)
                .email("owner@acme.com")
                .passwordHash("someHashedValue")
                .build();

        Company fakeCompany = Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.PENDING)
                .build();

        when(companyUserRepository.findByEmail("owner@acme.com"))
                .thenReturn(Optional.of(fakeUser));

        when(companyRepository.findById(fakeUser.getCompanyId()))
                .thenReturn(Optional.of(fakeCompany));

        InvalidCredentialsException thrown=assertThrows(
                InvalidCredentialsException.class,
                () -> companyOnboardingService.verifyLogin("owner@acme.com", "Password123")
        );
        assertEquals("Company account is not active.", thrown.getMessage());
    }

    @Test
    void verifyLogin_credentialExpired_throwsCredentialExpiredException(){

        CompanyUser fakeUser = CompanyUser.builder()
                .companyId(1L)
                .email("owner@acme.com")
                .passwordHash("someHashedValue")
                .mustChangePassword(true)
                .build();
        Company fakeCompany = Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.ACTIVE)
                .build();

        when(companyUserRepository.findByEmail("owner@acme.com"))
                .thenReturn(Optional.of(fakeUser));

        when(companyRepository.findById(fakeUser.getCompanyId()))
                .thenReturn(Optional.of(fakeCompany));

        BCryptPasswordEncoder fakeEncoder=mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.matches(anyString(),anyString())).thenReturn(true);

        doThrow(new CredentialExpiredException("Your temporary password has expired. Ask an administrator to resend your credentials."))
                .when(credentialPolicy).enforceNotExpired(anyBoolean(),any());

        CredentialExpiredException thrown=assertThrows(
                CredentialExpiredException.class,
                () -> companyOnboardingService.verifyLogin("owner@acme.com", "Password123")
        );
        assertEquals("Your temporary password has expired. Ask an administrator to resend your credentials.", thrown.getMessage());
    }


    @Test
    void verifyLogin_success_returnsAuthenticatedCompanyUser(){

        CompanyUser fakeUser = CompanyUser.builder()
                .id(10L)
                .companyId(1L)
                .email("owner@acme.com")
                .passwordHash("someHashedValue")
                .role(Role.OWNER)
                .mustChangePassword(false)
                .build();
        Company fakeCompany = Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.ACTIVE)
                .build();


        when(companyUserRepository.findByEmail("owner@acme.com"))
                .thenReturn(Optional.of(fakeUser));

        when(companyRepository.findById(fakeUser.getCompanyId()))
                .thenReturn(Optional.of(fakeCompany));

        BCryptPasswordEncoder fakeEncoder = mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthenticatedCompanyUser result = assertDoesNotThrow(
                () -> companyOnboardingService.verifyLogin("owner@acme.com", "Password123")
        );

        assertEquals(fakeUser.getId(), result.getId());
        assertEquals(fakeUser.getCompanyId(), result.getCompanyId());
        assertEquals(fakeUser.getRole(), result.getRole());
        assertEquals(fakeUser.isMustChangePassword(), result.isMustChangePassword());
    }

    @Test
    void approve_companyNotFound_throwsCompanyNotFoundException(){

        when(companyRepository.findById(1L))
                .thenReturn(Optional.empty());

        CompanyNotFoundException thrown=assertThrows(CompanyNotFoundException.class
        ,()->companyOnboardingService.approve(1L,4L));

        assertEquals("no company found with  id1",thrown.getMessage());
    }

    @Test
    void approve_companyNotPending_throwsInvalidCompanyStateException(){

        Company fakeCompany=Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.ACTIVE)
                .build();

        when(companyRepository.findById(fakeCompany.getId()))
                .thenReturn(Optional.of(fakeCompany));

        InvalidCompanyStateException thrown=assertThrows(InvalidCompanyStateException.class,
                ()->companyOnboardingService.approve(1L,4L));

        assertEquals("Company 1 is not in PENDING status. Current status: ACTIVE" ,thrown.getMessage());
    }

    @Test
    void approve_pendingCompany_activatesCompanyAndCreatesOwner(){

        Company fakeCompany = Company.builder()
                .id(1L)
                .companyStatus(CompanyStatus.PENDING)
                .contactEmail("owner@acme.com")
                .companyName("Acme Inc")
                .companyCode("ACME01")
                .build();

        when(companyRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompany));

        when(credentialGenerator.generateRawPassword())
                .thenReturn("TempPass123!");


        BCryptPasswordEncoder fakeEncoder = mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.encode("TempPass123!")).thenReturn("encodedHashXYZ");

        companyOnboardingService.approve(1L, 4L);

        assertEquals(CompanyStatus.ACTIVE, fakeCompany.getCompanyStatus());

        ArgumentCaptor<CompanyUser> userCaptor=ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(userCaptor.capture());
        CompanyUser savedOwner = userCaptor.getValue();
        assertEquals(1L, savedOwner.getCompanyId());
        assertEquals("owner@acme.com", savedOwner.getEmail());
        assertEquals(Role.OWNER, savedOwner.getRole());
        assertTrue(savedOwner.isMustChangePassword());
        assertEquals("encodedHashXYZ", savedOwner.getPasswordHash());

        ArgumentCaptor<CompanyDecision> decisionCaptor=ArgumentCaptor.forClass(CompanyDecision.class);
        verify(decisionRepository).save(decisionCaptor.capture());
        CompanyDecision savedDecision = decisionCaptor.getValue();
        assertEquals(1L, savedDecision.getCompanyId());
        assertEquals(4L, savedDecision.getAdminId());
        assertEquals(DecisionType.APPROVED, savedDecision.getDecision());


        ArgumentCaptor<CompanyApprovedEvent> eventCaptor = ArgumentCaptor.forClass(CompanyApprovedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CompanyApprovedEvent event = eventCaptor.getValue();
        assertEquals("owner@acme.com", event.getContactEmail());
        assertEquals("Acme Inc", event.getCompanyName());
        assertEquals("ACME01", event.getCompanyCode());
        assertEquals("TempPass123!", event.getRawOwnerPassword());
        assertEquals(fakeCompany.getApiHashCode(), ApiKeyGenerator.hash(event.getRawApiKey()));
    }


}
