package com.finpulse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record LimitRule(int maxAttempts, long windowSeconds) {}

    private static final Map<String, LimitRule> LIMITED_ENDPOINTS = Map.of(
            "/api/companies/register", new LimitRule(3, 3600),
            "/api/companies/status", new LimitRule(15, 600),
            "/api/auth/company/login", new LimitRule(5, 600),
            "/api/auth/company/forgot-password", new LimitRule(3, 900)
    );

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        LimitRule rule = LIMITED_ENDPOINTS.get(request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRequestURI() + ":" + getClientIp(request);
        Deque<Instant> history = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (history) {
            Instant now = Instant.now();
            while (!history.isEmpty() && history.peekFirst().isBefore(now.minusSeconds(rule.windowSeconds()))) {
                history.pollFirst();
            }
            if (history.size() >= rule.maxAttempts()) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"Too many attempts. Please try again later.\"}");
                return;
            }
            history.addLast(now);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}