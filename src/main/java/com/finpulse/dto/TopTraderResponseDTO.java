package com.finpulse.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopTraderResponseDTO {
    
            private String senderAccount;

            private Long transactionCount;

            private BigDecimal totalAmount;

}
