package com.finpulse.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomAuthEntryPoint entryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/companies/register", "/api/companies/status/**").permitAll()
                        .requestMatchers("/api/v1/ledger/upload").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("ADMIN_OWNER")

                        .requestMatchers("/api/admin/register", "/api/admin/*/regenerate")
                        .hasAuthority("ADMIN_OWNER")
                        .requestMatchers("/api/admin/**")
                        .hasAnyAuthority("ADMIN_OWNER", "ADMIN_MEMBER")

                        .requestMatchers("/api/company/users", "/api/company/users/*/regenerate")
                        .hasAuthority("COMPANY_OWNER")
                        .requestMatchers("/api/company/**")
                        .hasAnyAuthority("COMPANY_OWNER", "COMPANY_MEMBER")


                        .requestMatchers("/api/v1/fraud-findings/**",
                                "/api/v1/ledger/notifications/**",
                                "/api/v1/ledger/rejections")
                        .hasAnyAuthority("COMPANY_OWNER", "COMPANY_MEMBER")

                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, RateLimitFilter.class);

        return http.build();
    }
}
