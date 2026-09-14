package com.finpulse.security;

import com.finpulse.exception.CredentialExpiredException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class CredentialPolicyTest {

    private final CredentialPolicy credentialPolicy = new CredentialPolicy();

    @Test
    void enforceNotExpired_mustChangePasswordFalse_doesNotThrow() {
        assertDoesNotThrow(() ->
                credentialPolicy.enforceNotExpired(false, LocalDateTime.now().minusDays(1)));
    }

    @Test
    void enforceNotExpired_expiryIsNull_doesNotThrow(){
        assertDoesNotThrow(() ->
                credentialPolicy.enforceNotExpired(true,null));
    }

    @Test
    void enforceNotExpired_expiryInFuture_doesNotThrow(){
        assertDoesNotThrow(() ->
                credentialPolicy.enforceNotExpired(true,LocalDateTime.now().plusDays(1)));
    }

    @Test
    void enforceNotExpired_mustChangeAndExpired_throwsCredentialExpiredException(){
        CredentialExpiredException thrown=assertThrows(CredentialExpiredException.class,
                ()->credentialPolicy.enforceNotExpired(true,LocalDateTime.now().minusDays(1)));
        assertEquals("Your temporary password has expired. Ask an administrator to resend your credentials.",thrown.getMessage());
    }
}
