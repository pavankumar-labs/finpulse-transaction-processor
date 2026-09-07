package com.finpulse.security;

import com.finpulse.entity.SubjectType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class FinPulseUserDetails implements UserDetails{

    private final Long subjectId;
    private final SubjectType subjectType;
    private final Long companyId;
    private final String passwordHash;
    private final String compoundAuthority;
    private final boolean mustChangePassword;



    public FinPulseUserDetails(Long subjectId, SubjectType subjectType, Long companyId,
                               String passwordHash, String compoundAuthority, boolean mustChangePassword) {
        this.subjectId = subjectId;
        this.subjectType = subjectType;
        this.companyId = companyId;
        this.passwordHash = passwordHash;
        this.compoundAuthority = compoundAuthority;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public Long getCompanyId() {
        return companyId;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return String.valueOf(subjectId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(compoundAuthority));
    }

    public String getCompoundAuthorityRole() {
        return compoundAuthority.substring(compoundAuthority.indexOf('_') + 1);
    }

}
