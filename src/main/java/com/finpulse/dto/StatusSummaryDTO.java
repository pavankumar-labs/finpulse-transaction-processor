package com.finpulse.dto;

import com.finpulse.entity.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatusSummaryDTO {

    private ProcessingStatus status;

    private long transactionCount;

}