package com.finpulse.dto;



import java.time.LocalDateTime;
import java.util.List;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HighReceiverFraudResponseDTO {

    private LocalDateTime generatedAt;

    private long totalSuspiciousAccounts;

    private List<HighReceiverAccountResponseDTO> accounts;
}
