package com.finpulse.dto;

import com.finpulse.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthenticatedCompanyUser {

    private final Long id;
    private final Long companyId;
    private final Role role;
    private final boolean mustChangePassword;
}
