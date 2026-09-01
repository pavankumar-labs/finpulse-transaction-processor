package com.finpulse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PendingCompanyDTO {

    private Long id;
    private String companyCode;
    private String companyName;
    private String companyUrl;
    private LocalDateTime createdAt;
}
