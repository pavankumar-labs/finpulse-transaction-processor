package com.finpulse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SuspiciousAccountResponseDTO {

    private String  senderAccount;

    private Long transactionCount;

    private BigDecimal totalAmount;

    private LocalDateTime firstTransactionTime;

    private LocalDateTime lastTransactionTime;

}