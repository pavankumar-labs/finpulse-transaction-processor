package com.finpulse.dto;

import com.finpulse.entity.RiskLevel;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;


@Data
@Builder
public class FraudFindingResolvedResponseDTO {

    private String accountNumber;
    private String fileProcessingId;
    private RiskLevel riskLevel;
    private LocalDateTime resolvedAt;

}
