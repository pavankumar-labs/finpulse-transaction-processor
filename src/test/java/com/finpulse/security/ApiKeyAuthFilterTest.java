package com.finpulse.security;

import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.repository.CompanyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private CompanyRepository companyRepository;
    private ApiKeyAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    private static final String RAW_KEY = "fp_live_test-raw-key-value";

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        filter = new ApiKeyAuthFilter(companyRepository);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }


    private Company buildCompany(Long id, CompanyStatus status){
        return Company.builder()
                .id(id)
                .apiHashCode(ApiKeyGenerator.hash(RAW_KEY))
                .companyStatus(status)
                .build();
    }

    @Test
    void doFilterInternal_allowsRequest_whenCompanyStatusIsActive()
            throws Exception{
        Company activeCompany = buildCompany(42L, CompanyStatus.ACTIVE);
        when(request.getHeader("X-API-Key")).thenReturn(RAW_KEY);
        when(companyRepository.findByApiHashCode(anyString())).thenReturn(Optional.of(activeCompany));

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute(ApiKeyAuthFilter.COMPANY_ID_ATTRIBUTE, 42L);
        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = CompanyStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void doFilterInternal_rejectsWithForbidden_whenCompanyStatusIsNotActive
            (CompanyStatus nonActiveStatus) throws Exception{
        Company company = buildCompany(1L, nonActiveStatus);
        when(request.getHeader("X-API-Key")).thenReturn(RAW_KEY);
        when(companyRepository.findByApiHashCode(anyString())).thenReturn(Optional.of(company));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Company is not active");
        verify(filterChain, never()).doFilter(any(), any());
        verify(request, never()).setAttribute(eq(ApiKeyAuthFilter.COMPANY_ID_ATTRIBUTE), any());
    }

    @Test
    void doFilterInternal_rejectsWithUnauthorized_whenApiKeyHeaderMissing()
            throws Exception{
        when(request.getHeader("X-API-Key")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key header");
        verify(filterChain, never()).doFilter(any(), any());
        verifyNoInteractions(companyRepository);
    }

    @Test
    void doFilterInternal_rejectsWithUnauthorized_whenApiKeyHeaderIsBlank()
            throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key header");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_rejectsWithUnauthorized_whenApiKeyDoesNotMatchAnyCompany()
            throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(RAW_KEY);
        when(companyRepository.findByApiHashCode(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
        verify(filterChain, never()).doFilter(any(), any());
    }

}
