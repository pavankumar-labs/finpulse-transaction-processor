package com.finpulse.service;

import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.CompanyUser;
import com.finpulse.entity.Role;
import com.finpulse.event.CredentialIssuedEvent;
import com.finpulse.exception.AccessDeniedException;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.repository.CompanyUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static com.finpulse.entity.Role.MEMBER;
import static com.finpulse.entity.Role.OWNER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompanyUserManagementServiceTest {

    @Mock private  CompanyUserRepository companyUserRepository;
    @Mock private  CredentialGenerator credentialGenerator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private  PasswordEncoderConfig passwordEncoderConfig;

    @InjectMocks
    private CompanyUserManagementService companyUserManagementService;

    @Test
    void createTeammate_callerNotOwner_throwsAccessDeniedException(){
        AccessDeniedException thrown=assertThrows(AccessDeniedException.class,
                ()->companyUserManagementService.createTeammate(1L, MEMBER,"user@email.com"));
        assertEquals("Only the account owner can add new users.",thrown.getMessage());
    }

    @Test
    void createTeammate_emailAlreadyExists_throwsIllegalArgumentException(){

        CompanyUser existingUser = CompanyUser.builder().build();
        when(companyUserRepository.findByEmail("user@email.com"))
                .thenReturn(Optional.of(existingUser));

        IllegalArgumentException thrown=assertThrows(IllegalArgumentException.class,
                ()->companyUserManagementService.createTeammate(1L, OWNER,"user@email.com"));
        assertEquals("A user with this email already exists.",thrown.getMessage());
    }

    @Test
    void createTeammate_success_createsMemberAndPublishesEvent(){
        when(companyUserRepository.findByEmail("newuser@email.com"))
                .thenReturn(Optional.empty());

        when(credentialGenerator.generateRawPassword()).thenReturn("TempPass123!");

        BCryptPasswordEncoder fakeEncoder = mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.encode("TempPass123!")).thenReturn("encodedTempHash");

        assertDoesNotThrow(() ->
                companyUserManagementService.createTeammate(1L, Role.OWNER, "newuser@email.com"));

        ArgumentCaptor<CompanyUser> userCaptor = ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyUserRepository).save(userCaptor.capture());
        CompanyUser savedMember = userCaptor.getValue();

        assertEquals(1L, savedMember.getCompanyId());
        assertEquals("newuser@email.com", savedMember.getEmail());
        assertEquals(Role.MEMBER, savedMember.getRole());
        assertTrue(savedMember.isMustChangePassword());
        assertEquals("encodedTempHash", savedMember.getPasswordHash());

        ArgumentCaptor<CredentialIssuedEvent> eventCaptor = ArgumentCaptor.forClass(CredentialIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CredentialIssuedEvent event = eventCaptor.getValue();

        assertEquals("newuser@email.com", event.getEmail());
        assertEquals("TempPass123!", event.getRawPassword());
    }

    @Test
    void regenerateTeammateCredentials_callerNotOwner_throwsAccessDeniedException(){
        AccessDeniedException thrown=assertThrows(AccessDeniedException.class,
                ()->companyUserManagementService.regenerateTeammateCredentials(1L, MEMBER,2L));
        assertEquals("Only the account owner can regenerate credentials.",thrown.getMessage());
    }

    @Test
    void regenerateTeammateCredentials_targetUserNotFound_throwsInvalidCredentialsException(){
        when(companyUserRepository.findById(2L))
                .thenReturn(Optional.empty());
        InvalidCredentialsException thrown=assertThrows(InvalidCredentialsException.class,
                ()->companyUserManagementService.regenerateTeammateCredentials(1L,OWNER,2L));
        assertEquals("User not found.",thrown.getMessage());
    }

    @Test
    void regenerateTeammateCredentials_targetInDifferentCompany_throwsAccessDeniedException(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                        .id(2L)
                .companyId(2L)
                                .build();
        when(companyUserRepository.findById(2L))
                .thenReturn(Optional.of(fakeCompanyUser));

        AccessDeniedException thrown=assertThrows(AccessDeniedException.class,
                ()->companyUserManagementService.regenerateTeammateCredentials(1L, OWNER,2L));
        assertEquals("Cannot manage users outside your own company.",thrown.getMessage());
    }

    @Test
    void regenerateTeammateCredentials_success_regeneratesCredentials(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                .id(2L)
                .companyId(1L)
                .email("user@gmail.com")
                .passwordHash("hashedpassword")
                .build();
        when(companyUserRepository.findById(2L))
                .thenReturn(Optional.of(fakeCompanyUser));
        when(credentialGenerator.generateRawPassword()).thenReturn("rawpassword");
        BCryptPasswordEncoder fakeEncoder = mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.encode("rawpassword"))
                .thenReturn("hashedpassword");

        assertDoesNotThrow(() ->
                companyUserManagementService.regenerateTeammateCredentials(1L, Role.OWNER, 2L));


        assertEquals("hashedpassword",fakeCompanyUser.getPasswordHash());
        assertTrue(fakeCompanyUser.isMustChangePassword());
        verify(companyUserRepository).save(fakeCompanyUser);

        ArgumentCaptor<CredentialIssuedEvent> eventCaptor = ArgumentCaptor.forClass(CredentialIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CredentialIssuedEvent event = eventCaptor.getValue();

        assertEquals(event.getEmail(),fakeCompanyUser.getEmail());
        assertEquals(event.getRawPassword(),"rawpassword");
    }
}
