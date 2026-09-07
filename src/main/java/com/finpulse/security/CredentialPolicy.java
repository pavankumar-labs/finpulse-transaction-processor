package com.finpulse.security;

import com.finpulse.exception.CredentialExpiredException;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class CredentialPolicy {

    public void enforceNotExpired(boolean mustChangePassword, LocalDateTime credentialExpiresAt){

        if (mustChangePassword && credentialExpiresAt != null
                && credentialExpiresAt.isBefore(LocalDateTime.now())) {
            throw new CredentialExpiredException(
                    "Your temporary password has expired. Ask an administrator to resend your credentials.");
        }
    }

}
