package com.finpulse.testsupport;

import com.finpulse.entity.SubjectType;
import com.finpulse.security.FinPulseUserDetails;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockCompanyUser.Factory.class)
public @interface WithMockCompanyUser {
    long companyId() default 1L;
    String role() default "COMPANY_OWNER";

    class Factory implements WithSecurityContextFactory<WithMockCompanyUser> {
        @Override
        public SecurityContext createSecurityContext(WithMockCompanyUser annotation) {
            FinPulseUserDetails principal = new FinPulseUserDetails(
                    99L, SubjectType.COMPANY_USER, annotation.companyId(),
                    "irrelevant-hash", "COMPANY_" + annotation.role(), false
            );
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContext context = new SecurityContextImpl();
            context.setAuthentication(auth);
            return context;
        }
    }
}