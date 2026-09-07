package com.finpulse.security;

import com.finpulse.entity.SubjectType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import io.jsonwebtoken.*;

import java.util.Date;


@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-minutes}")
    private long accessTokenExpiryMinutes;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }


    public String generateAccessToken(Long subjectId, SubjectType type, Long companyId,
                                      String role, boolean mustChangePassword){

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim("type", type.name())
                .claim("mustChangePassword", mustChangePassword)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMinutes * 60_000))
                .signWith(key());

        if (companyId != null) builder.claim("companyId", companyId);
        if (role != null) builder.claim("role", role);

        return builder.compact();

    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
