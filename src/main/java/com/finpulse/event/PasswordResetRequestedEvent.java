package com.finpulse.event;

import lombok.Getter;

@Getter
public class PasswordResetRequestedEvent {

    private final String email;
    private final String rawToken;

    public PasswordResetRequestedEvent(String email, String rawToken) {
        this.email = email;
        this.rawToken = rawToken;
}
