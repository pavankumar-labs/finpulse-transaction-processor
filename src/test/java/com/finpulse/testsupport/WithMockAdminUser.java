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
@WithSecurityContext(factory = WithMockAdminUser.Factory.class)
public @interface WithMockAdminUser {
    long adminId() default 1L;
    String role() default "OWNER";

    class Factory implements WithSecurityContextFactory<WithMockAdminUser> {
        @Override
        public SecurityContext createSecurityContext(WithMockAdminUser annotation) {
            FinPulseUserDetails principal = new FinPulseUserDetails(
                    annotation.adminId(), SubjectType.ADMIN, null,
                    "irrelevant-hash", "ADMIN_" + annotation.role(), false
            );
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContext context = new SecurityContextImpl();
            context.setAuthentication(auth);
            return context;
        }
    }
}
