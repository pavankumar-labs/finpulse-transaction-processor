package com.finpulse.event;

import lombok.Getter;

@Getter
public class CredentialIssuedEvent {

    private final String email;
    private final String rawPassword;

    public CredentialIssuedEvent(String email, String rawPassword) {
        this.email = email;
        this.rawPassword = rawPassword;

    }
}
