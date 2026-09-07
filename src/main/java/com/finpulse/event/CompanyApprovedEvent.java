package com.finpulse.event;

import lombok.Getter;

@Getter
public class CompanyApprovedEvent {

    private final String contactEmail;
    private final String companyName;
    private final String companyCode;
    private final String rawApiKey;
    private final String rawOwnerPassword;

    public CompanyApprovedEvent(String contactEmail, String companyName, String companyCode,
                                String rawApiKey, String rawOwnerPassword) {
        this.contactEmail = contactEmail;
        this.companyName = companyName;
        this.companyCode = companyCode;
        this.rawApiKey = rawApiKey;
        this.rawOwnerPassword = rawOwnerPassword;

    }
}
