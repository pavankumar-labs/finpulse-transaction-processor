package com.finpulse.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepeatFraudResponseDTO {

    private LocalDateTime generatedAt;
    private long totalSuspiciousAccounts;
    private List<RepeatTransferResponseDTO> accounts;

}
