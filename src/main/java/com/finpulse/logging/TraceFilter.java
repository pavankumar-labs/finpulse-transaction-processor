package com.finpulse.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID="correlationId";


    @Override
    protected  void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response, FilterChain chain)
    throws IOException, ServletException {

        try {
            String correlationId= UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID,correlationId);
            response.setHeader("X-CorrelationId-Id",correlationId);
            chain.doFilter(request,response);
        }
        finally {
            MDC.clear();
        }


    }
}
