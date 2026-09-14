package com.finpulse.security;

import com.finpulse.entity.SubjectType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    JwtUtil jwtUtil=new JwtUtil();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-that-is-at-least-32-bytes-long!");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiryMinutes", 15L);
    }

    @Test
    void  generateAccessToken_withCompanyIdAndRole_includesAllClaims(){
        String token = jwtUtil.generateAccessToken(10L, SubjectType.COMPANY_USER, 1L, "OWNER", false);
        Claims claims = jwtUtil.parseAndValidate(token);

        assertEquals("10", claims.getSubject());
        assertEquals(SubjectType.COMPANY_USER.name(), claims.get("type", String.class));
        assertEquals(1L, ((Number) claims.get("companyId")).longValue());
        assertEquals("OWNER", claims.get("role", String.class));
        assertEquals(false, claims.get("mustChangePassword", Boolean.class));
        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getExpiration().after(new java.util.Date()));
    }
    @Test
    void generateAccessToken_adminToken_omitsCompanyId(){
        String token = jwtUtil.generateAccessToken(10L, SubjectType.ADMIN, null, "OWNER", false);
        Claims claims = jwtUtil.parseAndValidate(token);

        assertEquals("10", claims.getSubject());
        assertEquals(SubjectType.ADMIN.name(), claims.get("type", String.class));
        assertNull(claims.get("companyId"));
        assertEquals("OWNER", claims.get("role", String.class));
        assertEquals(false, claims.get("mustChangePassword", Boolean.class));
    }

    @Test
    void parseAndValidate_tokenSignedWithDifferentKey_throwsJwtException(){

        JwtUtil otherJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(otherJwtUtil, "secret", "a-completely-different-secret-key-32-bytes-plus!");
        ReflectionTestUtils.setField(otherJwtUtil, "accessTokenExpiryMinutes", 15L);

        String tokenSignedByOtherKey = otherJwtUtil.generateAccessToken(
                10L, SubjectType.COMPANY_USER, 1L, "OWNER", false);

        assertThrows(JwtException.class, () ->
                jwtUtil.parseAndValidate(tokenSignedByOtherKey));
    }
}
