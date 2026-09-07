package com.finpulse.service;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class CredentialGenerator {

    private static final String CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private static final int LENGTH = 14;
    private final SecureRandom random = new SecureRandom();

    public String generateRawPassword(){

        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}