package com.finpulse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RejectCompanyRequestDTO {
    @NotNull(message = "reason should be mentioned ")
    private String reason;
}
