package com.finpulse.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HighReceiverAccountResponseDTO {

    private String receiverAccount;

    private Long distinctSenderCount;

    private LocalDateTime firstTransactionTime;

    private LocalDateTime lastTransactionTime;
}
