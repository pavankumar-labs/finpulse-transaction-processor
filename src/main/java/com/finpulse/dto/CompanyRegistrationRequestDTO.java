package com.finpulse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRegistrationRequestDTO {

    @NotBlank(message = "companyCode is required")
    private String companyCode;

    @NotBlank(message = "companyName is required")
    private String companyName;

    @NotBlank(message = "contactEmail is required")
    @Email(message = "contactEmail must be a valid email address")
    private String contactEmail;

    private String companyUrl;
}
