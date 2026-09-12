package com.finpulse.service;

import com.finpulse.config.PasswordEncoderConfig;
import com.finpulse.entity.CompanyPasswordResetToken;
import com.finpulse.entity.CompanyUser;
import com.finpulse.event.CompanyApprovedEvent;
import com.finpulse.event.PasswordResetRequestedEvent;
import com.finpulse.exception.InvalidCredentialsException;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.repository.CompanyPasswordResetTokenRepository;
import com.finpulse.repository.CompanyUserRepository;
import com.finpulse.security.ApiKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyAuthSelfServiceServiceTest {

    @Mock private CompanyPasswordResetTokenRepository resetTokenRepository;
    @Mock private CompanyUserRepository companyUserRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PasswordEncoderConfig passwordEncoderConfig;

    @InjectMocks
    private CompanyAuthSelfServiceService companyAuthSelfServiceService;

    @Test
    void resetPassword_tokenNotFound_throwsInvalidTokenException(){
        when(resetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        InvalidTokenException thrown=assertThrows(InvalidTokenException.class,
                ()->companyAuthSelfServiceService.resetPassword("someRawToken", "NewPass123!"));
        assertEquals("Invalid or expired reset link.",thrown.getMessage());
    }

    @Test
    void resetPassword_tokenAlreadyUsedOrExpired_throwsInvalidTokenException(){

        CompanyPasswordResetToken fakeResetToken=CompanyPasswordResetToken.builder()
                .build();

        when(resetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(fakeResetToken));
        when(resetTokenRepository.markUsedIfValid(anyString(), any()))
                .thenReturn(0);
        InvalidTokenException thrown=assertThrows(InvalidTokenException.class,
                ()->companyAuthSelfServiceService.resetPassword("someRawToken", "NewPass123!"));
        assertEquals("This reset link has already been used or has expired.",thrown.getMessage());
    }

    @Test
    void resetPassword_userNotFound_throwsInvalidTokenException(){
        CompanyPasswordResetToken fakeResetToken=CompanyPasswordResetToken.builder()
                .companyUserId(4L)
                .build();

        when(resetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(fakeResetToken));
        when(resetTokenRepository.markUsedIfValid(anyString(), any()))
                .thenReturn(1);
        when(companyUserRepository.findById(fakeResetToken.getCompanyUserId()))
                .thenReturn(Optional.empty());

        InvalidTokenException thrown=assertThrows(InvalidTokenException.class,
                ()->companyAuthSelfServiceService.resetPassword("someRawToken", "NewPass123!"));
        assertEquals("User not found.",thrown.getMessage());
    }

    @Test
    void resetPassword_success_updatesUserPassword(){
        CompanyPasswordResetToken fakeResetToken = CompanyPasswordResetToken.builder()
                .companyUserId(5L)
                .build();

        CompanyUser fakeUser = CompanyUser.builder()
                .id(5L)
                .email("owner@acme.com")
                .passwordHash("oldHashValue")
                .mustChangePassword(true)
                .credentialExpiresAt(LocalDateTime.now().plusDays(3))
                .build();

        when(resetTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(fakeResetToken));

        when(resetTokenRepository.markUsedIfValid(anyString(), any()))
                .thenReturn(1);

        when(companyUserRepository.findById(fakeResetToken.getCompanyUserId()))
                .thenReturn(Optional.of(fakeUser));

        BCryptPasswordEncoder fakeEncoder = mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.encode("NewPass123!")).thenReturn("newEncodedHash");

        assertDoesNotThrow(() ->
                companyAuthSelfServiceService.resetPassword("someRawToken", "NewPass123!"));

        assertEquals("newEncodedHash", fakeUser.getPasswordHash());
        assertFalse(fakeUser.isMustChangePassword());
        assertNull(fakeUser.getCredentialExpiresAt());

        verify(companyUserRepository).save(fakeUser);
    }

    @Test
    void initiatePasswordReset_userNotFound_doesNothing(){
        when(companyUserRepository.findByEmail("nobody@acme.com"))
                .thenReturn(Optional.empty());

        companyAuthSelfServiceService.initiatePasswordReset("nobody@acme.com");

        verify(resetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void initiatePasswordReset_success_savesTokenAndPublishesEvent(){

        CompanyUser fakeCompanyUser=CompanyUser.builder()
                        .id(1L)
                .email("nobody@acme.com")
                .build();

        when(companyUserRepository.findByEmail("nobody@acme.com"))
                .thenReturn(Optional.of(fakeCompanyUser));

        companyAuthSelfServiceService.initiatePasswordReset("nobody@acme.com");

        ArgumentCaptor<CompanyPasswordResetToken> userCaptor=ArgumentCaptor.forClass(CompanyPasswordResetToken.class);
        verify(resetTokenRepository).save(userCaptor.capture());
        CompanyPasswordResetToken resetToken=userCaptor.getValue();
        assertEquals(fakeCompanyUser.getId(),resetToken.getCompanyUserId());
        assertFalse(resetToken.isUsed());

        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordResetRequestedEvent event = eventCaptor.getValue();
        assertEquals("nobody@acme.com", event.getEmail());
        assertNotEquals(event.getRawToken(),resetToken.getTokenHash());
    }

    @Test
    void changePassword_userNotFound_throwsInvalidCredentialsException(){
        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.empty());

        InvalidCredentialsException thrown=assertThrows(InvalidCredentialsException.class,
                ()->companyAuthSelfServiceService.changePassword(1L,"oldpassword","newPassword"));
        assertEquals("User not found.",thrown.getMessage());
    }

    @Test
    void changePassword_oldPasswordIncorrect_throwsInvalidCredentialsException(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                .id(1L)
                .passwordHash("hashed")
                .build();

        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompanyUser));
        BCryptPasswordEncoder fakeEncoder=mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.matches("oldpassword",fakeCompanyUser.getPasswordHash()))
                .thenReturn(false);

        InvalidCredentialsException thrown=assertThrows(InvalidCredentialsException.class,
                ()->companyAuthSelfServiceService.changePassword(1L,"oldpassword","newPassword"));
        assertEquals("Current password is incorrect.",thrown.getMessage());
    }

    @Test
    void changePassword_success_updatesPasswordHash(){
        CompanyUser fakeCompanyUser=CompanyUser.builder()
                .id(1L)
                .passwordHash("hashed")
                .mustChangePassword(true)
                .credentialExpiresAt(LocalDateTime.now().plusDays(3))
                .build();

        when(companyUserRepository.findById(1L))
                .thenReturn(Optional.of(fakeCompanyUser));
        BCryptPasswordEncoder fakeEncoder=mock(BCryptPasswordEncoder.class);
        when(passwordEncoderConfig.passwordEncoder()).thenReturn(fakeEncoder);
        when(fakeEncoder.matches("oldpassword",fakeCompanyUser.getPasswordHash()))
                .thenReturn(true);
        when(fakeEncoder.encode("newpassword")).thenReturn("newEncodedHash");

        assertDoesNotThrow(() ->
                companyAuthSelfServiceService.changePassword(1L, "oldpassword", "newpassword"));
        assertEquals("newEncodedHash",fakeCompanyUser.getPasswordHash());
        assertNull(fakeCompanyUser.getCredentialExpiresAt());
        assertFalse(fakeCompanyUser.isMustChangePassword());
    }



}
