package com.finpulse.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FraudDetectionResponseDTO {

    private LocalDateTime generatedAt;
    private long totalSuspiciousAccounts;
    private List<SuspiciousAccountResponseDTO> accounts;

}
