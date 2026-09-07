package com.finpulse.security;

import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import com.finpulse.exception.CompanyNotFoundException;
import com.finpulse.repository.CompanyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final CompanyRepository companyRepository;
    public static final String COMPANY_ID_ATTRIBUTE = "companyId";

    @Override
    protected void doFilterInternal
            (HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey=request.getHeader("X-API-Key");

        if(rawKey==null || rawKey.isBlank()){
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }

        String hashKey=ApiKeyGenerator.hash(rawKey);
        Optional<Company> companyOpt=companyRepository.findByApiHashCode(hashKey);

        if(companyOpt.isEmpty()){
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        Company company=companyOpt.get();
        if(company.getCompanyStatus()!= CompanyStatus.PENDING){
            response.sendError(HttpServletResponse.SC_FORBIDDEN,"Company is not active");
            return;
        }

        request.setAttribute(COMPANY_ID_ATTRIBUTE,company.getId());
        filterChain.doFilter(request,response);
    }
}
