package com.finpulse.config;

import com.finpulse.security.ApiKeyAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterFilterRegistration
            (ApiKeyAuthFilter filter){
        FilterRegistrationBean<ApiKeyAuthFilter> registration=new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/v1/ledger/upload");
        return registration;
    }
}
