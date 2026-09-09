package com.finpulse.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompanyRejectedEvent {

    private final String contactEmail;
    private final String companyName;
    private final String companyCode;

}