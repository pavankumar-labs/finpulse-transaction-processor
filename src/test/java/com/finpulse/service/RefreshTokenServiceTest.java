package com.finpulse.service;

import com.finpulse.entity.RefreshToken;
import com.finpulse.entity.SubjectType;
import com.finpulse.exception.InvalidTokenException;
import com.finpulse.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void validate_tokenNotFound_throwsInvalidTokenException(){

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());
        InvalidTokenException thrown=assertThrows(InvalidTokenException.class,
                ()->refreshTokenService.validate("jfklsjlkwlakkg"));
        assertEquals("Refresh token not recognized.",thrown.getMessage());
    }

    @Test
    void validate_tokenRevoked_throwsInvalidTokenException(){
        RefreshToken stored=RefreshToken.builder()
                        .revoked(true)
                .expiresAt(LocalDateTime.now().plusDays(1))
                                .build();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored));
        InvalidTokenException thrown=assertThrows(InvalidTokenException.class,
                ()->refreshTokenService.validate("jfklsjlkwlakkg"));
        assertEquals("Refresh token expired or revoked. Please log in again.",thrown.getMessage());
    }

    @Test
    void validate_tokenExpired_throwsInvalidTokenException(){
        RefreshToken stored=RefreshToken.builder()
                .revoked(false)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored));
        InvalidTokenException thrown=assertThrows(InvalidTokenException.class,
                ()->refreshTokenService.validate("jfklsjlkwlakkg"));
        assertEquals("Refresh token expired or revoked. Please log in again.",thrown.getMessage());
    }

    @Test
    void validate_success_returnsStoredToken(){
        RefreshToken stored=RefreshToken.builder()
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored));
        RefreshToken result = assertDoesNotThrow(() -> refreshTokenService.validate("jfklsjlkwlakkg"));
        assertSame(stored,result);
    }

    @Test
    void rotate_success_revokesOldTokenAndIssuesNewToken(){
        RefreshToken oldToken = RefreshToken.builder()
                .subjectType(SubjectType.COMPANY_USER)
                .subjectId(5L)
                .revoked(false)
                .tokenHash("oldHashValue")
                .build();

        String newRawToken = refreshTokenService.rotate(oldToken);
        assertTrue(oldToken.isRevoked());
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());

        List<RefreshToken> savedTokens = tokenCaptor.getAllValues();
        RefreshToken firstSave = savedTokens.get(0);
        RefreshToken secondSave = savedTokens.get(1);

        assertSame(oldToken, firstSave);

        assertEquals(SubjectType.COMPANY_USER, secondSave.getSubjectType());
        assertEquals(5L, secondSave.getSubjectId());
        assertFalse(secondSave.isRevoked());

        assertNotNull(newRawToken);
        assertNotEquals(oldToken.getTokenHash(), secondSave.getTokenHash());
    }

    @Test
    void revoke_tokenFound_marksRevokedAndSaves(){
        RefreshToken refreshToken=RefreshToken.builder()
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(refreshToken));
        assertDoesNotThrow(()->refreshTokenService.revoke("kjskfjlrgjljrlg"));
        assertTrue(refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void revoke_tokenNotFound_doesNothing(){
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());
        assertDoesNotThrow(()->refreshTokenService.revoke("kjskfjlrgjljrlg"));
        verify(refreshTokenRepository,never()).save(any());
    }
}
