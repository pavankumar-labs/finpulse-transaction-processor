package com.finpulse.security;

import com.finpulse.entity.SubjectType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final FinPulseUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final CustomAuthEntryPoint entryPoint;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException{

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try{
            Claims claims = jwtUtil.parseAndValidate(header.substring(7));

            Long subjectId = Long.valueOf(claims.getSubject());
            SubjectType subjectType = SubjectType.valueOf(claims.get("type", String.class));

            FinPulseUserDetails userDetails = userDetailsService.loadBySubject(subjectId, subjectType);

            if (!userDetails.isCredentialsNonExpired()) {
                entryPoint.commence(request, response,
                        new CredentialsExpiredException("Password must be changed before continuing."));
                return;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        catch (JwtException | IllegalArgumentException e) {
            entryPoint.commence(request, response, new org.springframework.security.core.AuthenticationException(e.getMessage()) {});
            return;
        }

        filterChain.doFilter(request, response);
    }
    }

