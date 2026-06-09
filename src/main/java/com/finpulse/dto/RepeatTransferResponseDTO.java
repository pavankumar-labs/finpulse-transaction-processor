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
public class RepeatTransferResponseDTO {

    private String senderAccount;
    private String receiverAccount;
    private Long transactionCount;
    private BigDecimal totalAmount;
    private LocalDateTime firstTransactionTime;
    private LocalDateTime lastTransactionTime;

}
