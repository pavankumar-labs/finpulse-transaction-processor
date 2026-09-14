package com.finpulse.security;

import com.finpulse.entity.SubjectType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    @Mock private FinPulseUserDetailsService userDetailsService;
    @Mock private JwtUtil jwtUtil;
    @Mock private CustomAuthEntryPoint entryPoint;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_noAuthorizationHeader_passesThrough() throws Exception {
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).parseAndValidate(anyString());
        verify(userDetailsService, never()).loadBySubject(any(), any());
    }

    @Test
    void doFilterInternal_headerNotBearer_passesThrough() throws Exception{
        request.addHeader("Authorization", "Basic somevalue");
        jwtAuthenticationFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).parseAndValidate(anyString());
        verify(userDetailsService, never()).loadBySubject(any(), any());
    }

    @Test
    void doFilterInternal_invalidToken_blocksAndCallsEntryPoint() throws Exception{
        request.addHeader("Authorization", "Bearer badtoken");
        when(jwtUtil.parseAndValidate("badtoken")).thenThrow(new JwtException("bad signature"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(entryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_mustChangePasswordNotExemptRoute_blocksAndCallsEntryPoint() throws Exception{
        request.addHeader("Authorization", "Bearer sometoken");
        request.setMethod("GET");
        request.setRequestURI("/api/some/other/endpoint");

        Claims fakeClaims = mock(Claims.class);
        when(fakeClaims.getSubject()).thenReturn("10");
        when(fakeClaims.get("type", String.class)).thenReturn("COMPANY_USER");
        when(jwtUtil.parseAndValidate("sometoken")).thenReturn(fakeClaims);

        FinPulseUserDetails fakeUserDetails = new FinPulseUserDetails(
                10L, SubjectType.COMPANY_USER, 1L, "hashedpw", "COMPANY_OWNER", true);
        when(userDetailsService.loadBySubject(10L, SubjectType.COMPANY_USER)).thenReturn(fakeUserDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(entryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_mustChangePasswordExemptRoute_allowsThroughAndSetsAuthentication() throws Exception{
        request.addHeader("Authorization", "Bearer sometoken");
        request.setMethod("POST");
        request.setRequestURI("/api/company/users/me/password");

        Claims fakeClaims = mock(Claims.class);
        when(fakeClaims.getSubject()).thenReturn("10");
        when(fakeClaims.get("type", String.class)).thenReturn("COMPANY_USER");
        when(jwtUtil.parseAndValidate("sometoken")).thenReturn(fakeClaims);

        FinPulseUserDetails fakeUserDetails = new FinPulseUserDetails(
                10L, SubjectType.COMPANY_USER, 1L, "hashedpw", "COMPANY_OWNER", true);
        when(userDetailsService.loadBySubject(10L, SubjectType.COMPANY_USER)).thenReturn(fakeUserDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(entryPoint, never()).commence(any(), any(), any());
        verify(filterChain).doFilter(request, response);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(fakeUserDetails, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

}
